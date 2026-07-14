import { ref } from 'vue'
import {
  fetchProjectWorkspaceState,
  saveProjectWorkspaceState,
} from '../api'
import type {
  AgentTab,
  ProjectWorkspaceState,
  SaveProjectWorkspaceStateRequest,
} from '../types/codemind'

export interface SaveCurrentWorkspaceStatePayload {
  selectedModuleId?: string | null
  selectedArtifactType?: string | null
  selectedArtifactId?: string | null
  activeAgentTab?: AgentTab | string | null
  uiState?: Record<string, unknown>
}

export function useProjectWorkspacePersistence(
  resolveWorkspaceId: () => string,
  resolveProjectCode: () => string,
) {
  const loadingWorkspaceState = ref(false)
  const savingWorkspaceState = ref(false)
  const workspaceStateError = ref<string | null>(null)

  async function loadPersistedWorkspaceState(): Promise<ProjectWorkspaceState | null> {
    loadingWorkspaceState.value = true
    workspaceStateError.value = null

    try {
      const workspaceId = resolveWorkspaceId()
      const projectCode = resolveProjectCode()

      if (!workspaceId || !projectCode) {
        return null
      }

      return await fetchProjectWorkspaceState(
        workspaceId,
        projectCode,
      )
    } catch (error) {
      workspaceStateError.value = getErrorMessage(error)
      console.error(error)
      return null
    } finally {
      loadingWorkspaceState.value = false
    }
  }

  async function saveCurrentWorkspaceState(
    payload: SaveCurrentWorkspaceStatePayload,
  ): Promise<ProjectWorkspaceState | null> {
    savingWorkspaceState.value = true
    workspaceStateError.value = null

    try {
      const workspaceId = resolveWorkspaceId()
      const projectCode = resolveProjectCode()

      if (!workspaceId || !projectCode) {
        return null
      }

      const request: SaveProjectWorkspaceStateRequest = {
        selectedModuleId: normalizeNullableString(payload.selectedModuleId),
        selectedArtifactType: normalizeNullableString(payload.selectedArtifactType),
        selectedArtifactId: normalizeNullableString(payload.selectedArtifactId),
        activeAgentTab: normalizeAgentTab(payload.activeAgentTab) || 'requirement',
        uiState: payload.uiState || {},
      }

      return await saveProjectWorkspaceState(
        workspaceId,
        projectCode,
        request,
      )
    } catch (error) {
      workspaceStateError.value = getErrorMessage(error)
      console.error(error)
      return null
    } finally {
      savingWorkspaceState.value = false
    }
  }

  function clearWorkspaceStateError() {
    workspaceStateError.value = null
  }

  return {
    loadingWorkspaceState,
    savingWorkspaceState,
    workspaceStateError,
    loadPersistedWorkspaceState,
    saveCurrentWorkspaceState,
    clearWorkspaceStateError,
  }
}

export function normalizeAgentTab(
  value: AgentTab | string | null | undefined,
): AgentTab | null {
  if (value === 'requirement') {
    return 'requirement'
  }

  if (value === 'development') {
    return 'development'
  }

  if (value === 'testing') {
    return 'testing'
  }

  return null
}

function normalizeNullableString(
  value: string | null | undefined,
): string | null {
  if (typeof value !== 'string') {
    return null
  }

  const trimmed = value.trim()

  if (!trimmed) {
    return null
  }

  return trimmed
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }

  if (typeof error === 'string') {
    return error
  }

  return 'Unknown workspace persistence error.'
}