import {
  ref,
} from 'vue'
import {
  fetchProjectThemeProfile,
  updateProjectThemeProfile,
} from '../api/themeProfileApi'
import type {
  ProjectThemeProfile,
  UpdateProjectThemeProfileRequest,
} from '../types/themeProfile'

export function useProjectThemeProfile(
  options: {
    resolveWorkspaceId: () => string
    resolveProjectCode: () => string
  },
) {
  const themeProfile = ref<ProjectThemeProfile | null>(null)
  const isLoadingThemeProfile = ref(false)
  const isSavingThemeProfile = ref(false)
  const themeProfileError = ref<string | null>(null)

  async function loadProjectThemeProfile() {
    const workspaceId = options.resolveWorkspaceId()
    const projectCode = options.resolveProjectCode()

    if (!workspaceId || !projectCode) {
      themeProfile.value = null
      return null
    }

    isLoadingThemeProfile.value = true
    themeProfileError.value = null

    try {
      const response = await fetchProjectThemeProfile(
        workspaceId,
        projectCode,
      )

      themeProfile.value = response
      return response
    } catch (error) {
      themeProfileError.value = resolveErrorMessage(
        error,
        '加载项目主题配置失败。',
      )
      throw error
    } finally {
      isLoadingThemeProfile.value = false
    }
  }

  async function saveProjectThemeProfile(
    payload: UpdateProjectThemeProfileRequest,
  ) {
    const workspaceId = options.resolveWorkspaceId()
    const projectCode = options.resolveProjectCode()

    if (!workspaceId || !projectCode) {
      throw new Error('缺少工作区或项目标识，无法保存主题配置。')
    }

    isSavingThemeProfile.value = true
    themeProfileError.value = null

    try {
      const response = await updateProjectThemeProfile(
        workspaceId,
        projectCode,
        payload,
      )

      themeProfile.value = response
      return response
    } catch (error) {
      themeProfileError.value = resolveErrorMessage(
        error,
        '保存项目主题配置失败。',
      )
      throw error
    } finally {
      isSavingThemeProfile.value = false
    }
  }

  return {
    themeProfile,
    isLoadingThemeProfile,
    isSavingThemeProfile,
    themeProfileError,
    loadProjectThemeProfile,
    saveProjectThemeProfile,
  }
}

function resolveErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}
