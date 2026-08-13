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

  if (!workspace?.workspaceId || !project?.projectCode) {
    console.error('OPEN_PROJECT_BLOCKED', {
      workspace,
      project,
    })
    window.alert('打开项目工作台失败：缺少当前工作空间或项目编号，请刷新项目列表后重试。')
    return
  }

  await openProject(project)

  const targetPath = `/workspaces/${encodeURIComponent(workspace.workspaceId)}/projects/${encodeURIComponent(project.projectCode)}`

  try {
    await router.push(targetPath)
  } catch (error) {
    console.error('OPEN_PROJECT_ROUTER_FAILED', error, {
      workspace,
      project,
      targetPath,
    })

    window.location.href = targetPath
  }
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