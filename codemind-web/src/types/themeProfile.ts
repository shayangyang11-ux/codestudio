// A9.10.14.4.3.15.4.4_STYLE_OVERRIDE_LOCK
export type ProjectIndustry =
  | 'GENERAL'
  | 'COMMERCE'
  | 'FOREIGN_TRADE'
  | 'WORK_ORDER'
  | 'WAREHOUSE'
  | 'MANUFACTURING'
  | 'CRM'
  | 'SAAS'
  | 'TECH'
  | 'INDUSTRIAL'
  | 'FINANCE'
  | 'GOV'
  | 'EDUCATION'
  | 'HEALTHCARE'
  | 'MEDICAL'

export type ProjectThemeKey =
  | 'enterprise_default'
  | 'tech_modern'
  | 'commerce_light'
  | 'industrial_pro'
  | 'finance_stable'
  | 'gov_clean'

export type ProjectVisualTone =
  | 'CLEAN'
  | 'TECH'
  | 'COMMERCE'
  | 'INDUSTRIAL'
  | 'FINANCE'
  | 'GOV'

export type ProjectThemeDensity =
  | 'COMPACT'
  | 'STANDARD'
  | 'COMFORTABLE'

export type ProjectThemeRadius =
  | 'SMALL'
  | 'MEDIUM'
  | 'LARGE'

export type ProjectLoginLayout =
  | 'BRAND_SPLIT'
  | 'CENTER_CARD'
  | 'MINIMAL'

export type ProjectNavigationStyle =
  | 'SIDE_NAV'
  | 'TOP_NAV'
  | 'MIXED'

export interface ProjectThemeProfile {
  id: string
  projectId: string
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
  source: string
  locked: boolean
  notes?: string | null
  createdBy?: string | null
  updatedBy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface UpdateProjectThemeProfileRequest {
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
  locked: boolean
  notes?: string | null
}

export const PROJECT_INDUSTRY_OPTIONS: Array<{
  value: ProjectIndustry
  label: string
  description: string
}> = [
  {
    value: 'GENERAL',
    label: '通用企业',
    description: '适合 OA、CRM、ERP、内部管理系统。',
  },
  {
    value: 'COMMERCE',
    label: '电商零售',
    description: '适合商城、订单、会员、用户中心等场景。',
  },
  {
    value: 'FOREIGN_TRADE',
    label: '外贸跨境',
    description: '适合询盘、报价、客户跟进、出口、物流和跨境业务。',
  },
  {
    value: 'WORK_ORDER',
    label: '工单服务',
    description: '适合派单、受理、处理、SLA、维修和客户服务流程。',
  },
  {
    value: 'WAREHOUSE',
    label: '仓储供应链',
    description: '适合入库、出库、库存、盘点、库位、供应商和 WMS。',
  },
  {
    value: 'MANUFACTURING',
    label: '制造生产',
    description: '适合生产、设备、产线、MES、质检、排产和工艺管理。',
  },
  {
    value: 'CRM',
    label: '客户关系',
    description: '适合客户、线索、商机、跟进、联系人和销售过程管理。',
  },
  {
    value: 'SAAS',
    label: '现代 SaaS',
    description: '适合数据平台、AI 平台、云服务、运营后台和现代工作台。',
  },
  {
    value: 'TECH',
    label: '科技平台',
    description: '适合 SaaS、AI、数据平台和数字化系统。',
  },
  {
    value: 'INDUSTRIAL',
    label: '工业制造',
    description: '适合设备、产线、MES、工控和工业互联网。',
  },
  {
    value: 'FINANCE',
    label: '金融风控',
    description: '适合支付、风控、账务、交易和金融后台。',
  },
  {
    value: 'GOV',
    label: '政务公共',
    description: '适合政务、监管、公共服务和办事系统。',
  },
  {
    value: 'EDUCATION',
    label: '教育平台',
    description: '适合教务、课程、学习平台和培训系统。',
  },
  {
    value: 'HEALTHCARE',
    label: '医疗健康',
    description: '适合医疗、护理、康复、机构管理场景。',
  },
]

export const PROJECT_THEME_OPTIONS: Array<{
  value: ProjectThemeKey
  label: string
  industry: ProjectIndustry
}> = [
  {
    value: 'enterprise_default',
    label: '企业默认',
    industry: 'GENERAL',
  },
  {
    value: 'tech_modern',
    label: '现代科技',
    industry: 'TECH',
  },
  {
    value: 'commerce_light',
    label: '清爽电商',
    industry: 'COMMERCE',
  },
  {
    value: 'industrial_pro',
    label: '工业专业',
    industry: 'INDUSTRIAL',
  },
  {
    value: 'finance_stable',
    label: '金融稳健',
    industry: 'FINANCE',
  },
  {
    value: 'gov_clean',
    label: '政务清晰',
    industry: 'GOV',
  },
]

export function defaultThemeKeyForIndustry(
  industry: ProjectIndustry,
): ProjectThemeKey {
  if (industry === 'COMMERCE') return 'commerce_light'
  if (industry === 'FOREIGN_TRADE') return 'enterprise_default'
  if (industry === 'WORK_ORDER') return 'tech_modern'
  if (industry === 'WAREHOUSE') return 'industrial_pro'
  if (industry === 'MANUFACTURING') return 'industrial_pro'
  if (industry === 'CRM') return 'enterprise_default'
  if (industry === 'SAAS') return 'tech_modern'
  if (industry === 'TECH') return 'tech_modern'
  if (industry === 'INDUSTRIAL') return 'industrial_pro'
  if (industry === 'FINANCE') return 'finance_stable'
  if (industry === 'GOV') return 'gov_clean'

  return 'enterprise_default'
}

export function visualToneForTheme(
  themeKey: ProjectThemeKey,
): ProjectVisualTone {
  if (themeKey === 'commerce_light') return 'COMMERCE'
  if (themeKey === 'tech_modern') return 'TECH'
  if (themeKey === 'industrial_pro') return 'INDUSTRIAL'
  if (themeKey === 'finance_stable') return 'FINANCE'
  if (themeKey === 'gov_clean') return 'GOV'

  return 'CLEAN'
}

export function defaultPrimaryColorForTheme(
  themeKey: ProjectThemeKey,
): string {
  if (themeKey === 'commerce_light') return '#f97316'
  if (themeKey === 'tech_modern') return '#4f46e5'
  if (themeKey === 'industrial_pro') return '#0f766e'
  if (themeKey === 'finance_stable') return '#1e3a8a'
  if (themeKey === 'gov_clean') return '#1d4ed8'

  return '#2563eb'
}
