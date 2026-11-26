import type {
  ProjectThemeProfile,
} from '../../types/themeProfile'
import type {
  UiRuntimeThemeProfile,
} from './uiContractTheme'

export function projectThemeToRuntimeTheme(
  profile: ProjectThemeProfile | null | undefined,
  fallback: UiRuntimeThemeProfile,
): UiRuntimeThemeProfile {
  if (!profile) {
    return fallback
  }

  return {
    ...fallback,
    key: profile.themeKey,
    label: labelForTheme(profile.themeKey),
    primary: profile.primaryColor || fallback.primary,
    secondary: profile.secondaryColor || fallback.secondary,
    accent: profile.accentColor || fallback.accent,
    density: densityToRuntime(profile.density),
    radius: radiusToCss(profile.radius),
    buttonShape: profile.radius === 'LARGE' ? 'rounded' : 'soft',
    visual: visualToRuntime(profile.visualTone),
  }
}

function labelForTheme(
  themeKey: ProjectThemeProfile['themeKey'],
): string {
  if (themeKey === 'commerce_light') return '电商零售'
  if (themeKey === 'tech_modern') return '科技平台'
  if (themeKey === 'industrial_pro') return '工业制造'
  if (themeKey === 'finance_stable') return '金融风控'
  if (themeKey === 'gov_clean') return '政务公共'

  return '通用企业'
}

function densityToRuntime(
  density: ProjectThemeProfile['density'],
): UiRuntimeThemeProfile['density'] {
  if (density === 'COMPACT') return 'compact'
  if (density === 'COMFORTABLE') return 'comfortable'

  return 'standard'
}

function radiusToCss(
  radius: ProjectThemeProfile['radius'],
): string {
  if (radius === 'SMALL') return '12px'
  if (radius === 'LARGE') return '22px'

  return '18px'
}

function visualToRuntime(
  visualTone: ProjectThemeProfile['visualTone'],
): UiRuntimeThemeProfile['visual'] {
  if (visualTone === 'COMMERCE') return 'commerce'
  if (visualTone === 'TECH') return 'tech'
  if (visualTone === 'INDUSTRIAL') return 'industrial'
  if (visualTone === 'FINANCE') return 'finance'
  if (visualTone === 'GOV') return 'gov'

  return 'clean'
}
