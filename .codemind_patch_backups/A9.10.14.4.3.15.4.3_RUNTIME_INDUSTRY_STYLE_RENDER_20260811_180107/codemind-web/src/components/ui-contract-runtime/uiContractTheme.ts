import type {
  UiContractComponent,
  UiContractContent,
} from '../../types/uiContract'
import type { ProjectThemeProfile } from '../../types/themeProfile'

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
  const formIdentity = pageKind === 'FORM_PAGE' ||
    layoutType === 'FORM_DETAIL' ||
    hasAny(identityText, ['创建', '新建', '新增', '编辑', '录入', '表单', 'create', 'new', 'edit', 'form'])

  if (formIdentity) return 'EDIT_FORM'

  if (hasAny(businessText, ['dashboard', '看板', '驾驶舱', '统计', '指标', '总览', '概览', '首页', '用户首页', '工作台', 'home', 'workbench'])) return 'DASHBOARD'
  if (hasAny(businessText, ['个人资料', '用户资料', 'profile', '资料页', '账户信息', '用户信息'])) return 'PROFILE_PAGE'
  if (hasAny(businessText, ['详情', 'detail', '明细'])) return 'DETAIL_PAGE'
  if (hasAny(businessText, ['搜索', '筛选', '查询', 'search', 'filter'])) return 'SEARCH_LIST'
  if (hasAny(businessText, ['列表', '表格', '分页', 'list', 'table', 'crud'])) return 'CRUD_LIST'
  if (hasAny(businessText, ['设置', '配置', 'setting', 'config'])) return 'SETTINGS_FORM'
  if (hasAny(businessText, ['上传', 'upload', '导入'])) return 'UPLOAD_FORM'
  if (hasAny(businessText, ['流程', '审批', '步骤', 'workflow', 'step'])) return 'WORKFLOW_STEP'
  if (hasAny(businessText, ['编辑', '修改', '保存', 'edit', 'form'])) return 'EDIT_FORM'

  // Feature text may refine business categories, but never auth categories.
  if (hasAny(featureText, ['用户信息', '用户资料', '退出登录', '状态守卫', '会话', 'session', 'logout'])) return 'DASHBOARD'
  if (hasAny(featureText, ['查询', '搜索', '列表', '分页', 'search', 'list'])) return 'SEARCH_LIST'
  if (hasAny(featureText, ['设置', '配置', '保存', 'setting', 'config', 'save'])) return 'SETTINGS_FORM'

  return 'GENERIC_PAGE'
}

export function resolveThemeProfile(
  contract: UiContractContent,
  projectThemeProfile?: ProjectThemeProfile | null,
): UiRuntimeThemeProfile {
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
  if (hasAny(text, ['工业', '制造', '设备', '产线', '工厂', 'mes', 'iot', '传感器', '监控'])) return THEME_PROFILES.industrial_pro
  if (hasAny(text, ['金融', '风控', '银行', '支付', '交易', '账务', 'finance', 'risk'])) return THEME_PROFILES.finance_stable
  if (hasAny(text, ['政务', '政府', '公共服务', '监管', 'gov'])) return THEME_PROFILES.gov_clean
  if (hasAny(text, ['科技', '智能', 'ai', '算法', '数据平台', 'saas', '云平台', '大数据', '数字化'])) return THEME_PROFILES.tech_modern

  return THEME_PROFILES.enterprise_default
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
  }
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