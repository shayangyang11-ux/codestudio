<template>
  <ProjectHomePage
    v-if="initialized && currentUser"
    :current-user="currentUser"
    :workspaces="workspaces"
    :selected-workspace-id="selectedWorkspace?.workspaceId || null"
    :projects="projects"
    :loading-projects="loadingProjects"
    :creating-project="creatingProject"
    :deleting-project-code="deletingProjectCode"
    @select-workspace="handleSelectWorkspace"
    @open-project="handleOpenProject"
    @create-project="handleCreateProject"
    @delete-project="handleDeleteProject"
    @logout="handleLogoutAndRedirect"
  />

  <div
    v-else
    class="page-loading"
  >
    正在加载工作区...
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import ProjectHomePage from '../components/ProjectHomePage.vue'
import { useAppSession } from '../composables/useAppSession'
import type {
  CreateProjectRequest,
  ProjectSummary,
  WorkspaceSummary,
} from '../types/codemind'

const router = useRouter()

const {
  initialized,
  currentUser,
  workspaces,
  selectedWorkspace,
  projects,
  loadingProjects,
  creatingProject,
  deletingProjectCode,

  bootstrapAuth,
  selectWorkspace,
  createProject,
  deleteProject,
  openProject,
  handleLogout,
} = useAppSession()

onMounted(async () => {
  await bootstrapAuth()
})

async function handleSelectWorkspace(workspace: WorkspaceSummary) {
  await selectWorkspace(workspace)
}

async function handleOpenProject(project: ProjectSummary) {
  const workspace = selectedWorkspace.value

  if (!workspace) {
    return
  }

  await openProject(project)

  await router.push({
    name: 'project-workspace',
    params: {
      workspaceId: workspace.workspaceId,
      projectCode: project.projectCode,
    },
  })
}

async function handleCreateProject(payload: CreateProjectRequest) {
  const createdProject = await createProject(payload)
  const workspace = selectedWorkspace.value

  if (!createdProject || !workspace) {
    return
  }

  await router.push({
    name: 'project-workspace',
    params: {
      workspaceId: workspace.workspaceId,
      projectCode: createdProject.projectCode,
    },
  })
}

async function handleDeleteProject(project: ProjectSummary) {
  await deleteProject(project)
}

async function handleLogoutAndRedirect() {
  handleLogout()

  await router.replace({
    name: 'login',
  })
}


</script>