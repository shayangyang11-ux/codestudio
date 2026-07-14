import { computed, ref } from 'vue'
import {
  clearAuthStorage,
  createWorkspaceProject,
  fetchCurrentUser,
  fetchCurrentUserWorkspaces,
  fetchWorkspaceProjects,
  getStoredAccessToken,
  getStoredCurrentUser,
} from '../api'
import type {
  CreateProjectRequest,
  CurrentUser,
  ProjectSummary,
  WorkspaceSummary,
} from '../types/codemind'

const lastWorkspaceKey = 'codemind:lastWorkspaceId'
const lastProjectKey = 'codemind:lastProjectCode'

const initialized = ref(false)
const initializing = ref(false)

const isAuthenticated = ref(false)
const currentUser = ref<CurrentUser | null>(null)

const workspaces = ref<WorkspaceSummary[]>([])
const selectedWorkspace = ref<WorkspaceSummary | null>(null)
const projects = ref<ProjectSummary[]>([])
const currentProject = ref<ProjectSummary | null>(null)

const loadingProjects = ref(false)
const creatingProject = ref(false)

const hasToken = computed(() => Boolean(getStoredAccessToken()))

export function useAppSession() {
  async function bootstrapAuth() {
    if (initializing.value) {
      return
    }

    initializing.value = true

    try {
      const token = getStoredAccessToken()

      if (!token) {
        clearSessionState()
        initialized.value = true
        return
      }

      currentUser.value = getStoredCurrentUser()

      try {
        currentUser.value = await fetchCurrentUser()
        isAuthenticated.value = true
        await loadInitialHomeData()
      } catch (error) {
        console.error(error)
        clearAuthStorage()
        clearSessionState()
      } finally {
        initialized.value = true
      }
    } finally {
      initializing.value = false
    }
  }

  async function handleLoginSuccess(user: CurrentUser) {
    currentUser.value = user
    isAuthenticated.value = true
    initialized.value = true

    await loadInitialHomeData()
  }

  function handleLogout() {
    clearAuthStorage()
    clearSessionState()
    initialized.value = true
  }

  async function loadInitialHomeData() {
    workspaces.value = await fetchCurrentUserWorkspaces()

    const savedWorkspaceId = localStorage.getItem(lastWorkspaceKey)
    const initialWorkspace =
      workspaces.value.find((workspace) => workspace.workspaceId === savedWorkspaceId) ||
      workspaces.value[0] ||
      null

    if (!initialWorkspace) {
      selectedWorkspace.value = null
      projects.value = []
      currentProject.value = null
      return
    }

    await selectWorkspace(initialWorkspace)

    const savedProjectCode = localStorage.getItem(lastProjectKey)
    const matchedProject = projects.value.find(
      (project) => project.projectCode === savedProjectCode,
    )

    if (matchedProject) {
      currentProject.value = matchedProject
    }
  }

  async function selectWorkspace(workspace: WorkspaceSummary) {
    selectedWorkspace.value = workspace
    currentProject.value = null

    localStorage.setItem(lastWorkspaceKey, workspace.workspaceId)

    loadingProjects.value = true

    try {
      projects.value = await fetchWorkspaceProjects(workspace.workspaceId)
    } finally {
      loadingProjects.value = false
    }
  }

  async function createProject(payload: CreateProjectRequest) {
    const workspace = selectedWorkspace.value

    if (!workspace) {
      return null
    }

    creatingProject.value = true

    try {
      const createdProject = await createWorkspaceProject(
        workspace.workspaceId,
        payload,
      )

      await selectWorkspace(workspace)
      currentProject.value = createdProject
      localStorage.setItem(lastProjectKey, createdProject.projectCode)

      return createdProject
    } finally {
      creatingProject.value = false
    }
  }

  async function openProject(project: ProjectSummary) {
    currentProject.value = project
    localStorage.setItem(lastProjectKey, project.projectCode)
  }

  async function ensureWorkspaceSelected(
    workspaceId: string,
  ): Promise<WorkspaceSummary | null> {
    if (!initialized.value) {
      await bootstrapAuth()
    }

    if (!isAuthenticated.value) {
      return null
    }

    let workspace = workspaces.value.find(
      (item) => item.workspaceId === workspaceId,
    )

    if (!workspace) {
      workspaces.value = await fetchCurrentUserWorkspaces()
      workspace = workspaces.value.find(
        (item) => item.workspaceId === workspaceId,
      )
    }

    if (!workspace) {
      return null
    }

    if (selectedWorkspace.value?.workspaceId !== workspace.workspaceId) {
      await selectWorkspace(workspace)
    }

    return workspace
  }

  async function ensureProjectSelected(
    workspaceId: string,
    projectCode: string,
  ): Promise<{
    workspace: WorkspaceSummary
    project: ProjectSummary
  } | null> {
    const workspace = await ensureWorkspaceSelected(workspaceId)

    if (!workspace) {
      return null
    }

    let project = projects.value.find(
      (item) => item.projectCode === projectCode,
    )

    if (!project) {
      projects.value = await fetchWorkspaceProjects(workspace.workspaceId)
      project = projects.value.find(
        (item) => item.projectCode === projectCode,
      )
    }

    if (!project) {
      return null
    }

    currentProject.value = project
    localStorage.setItem(lastProjectKey, project.projectCode)

    return {
      workspace,
      project,
    }
  }

  function clearSessionState() {
    isAuthenticated.value = false
    currentUser.value = null

    workspaces.value = []
    selectedWorkspace.value = null
    projects.value = []
    currentProject.value = null

    loadingProjects.value = false
    creatingProject.value = false
  }

  return {
    initialized,
    initializing,
    isAuthenticated,
    hasToken,
    currentUser,

    workspaces,
    selectedWorkspace,
    projects,
    currentProject,

    loadingProjects,
    creatingProject,

    bootstrapAuth,
    handleLoginSuccess,
    handleLogout,
    loadInitialHomeData,
    selectWorkspace,
    createProject,
    openProject,
    ensureWorkspaceSelected,
    ensureProjectSelected,
  }
}