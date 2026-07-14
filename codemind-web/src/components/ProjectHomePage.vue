<template>
  <div class="project-home">
    <header class="project-home-header">
      <div>
        <div class="home-brand-row">
          <div class="brand-logo">CM</div>
          <div>
            <h1>CodeMind Studio</h1>
            <p>企业级 AI Agent 开发平台</p>
          </div>
        </div>
      </div>

      <div class="user-card">
        <span>当前用户</span>
        <strong>{{ currentUser?.displayName || currentUser?.username || '-' }}</strong>
        <button
          class="logout-button"
          @click="$emit('logout')"
        >
          退出
        </button>
      </div>
    </header>

    <main class="project-home-main">
      <aside class="workspace-sidebar">
        <div class="sidebar-title">
          <h2>工作空间</h2>
          <p>个人空间与团队空间</p>
        </div>

        <button
          v-for="workspace in workspaces"
          :key="workspace.workspaceId"
          class="workspace-item"
          :class="{ active: workspace.workspaceId === selectedWorkspaceId }"
          @click="$emit('select-workspace', workspace)"
        >
          <div>
            <strong>{{ workspace.workspaceName }}</strong>
            <span>{{ workspace.workspaceCode }}</span>
          </div>

          <em>{{ workspace.workspaceType === 'PERSONAL' ? '个人' : '团队' }}</em>
        </button>
      </aside>

      <section class="project-list-panel">
        <div class="project-list-header">
          <div>
            <h2>项目列表</h2>
            <p v-if="selectedWorkspace">
              当前空间：{{ selectedWorkspace.workspaceName }}
            </p>
            <p v-else>
              请选择一个工作空间。
            </p>
          </div>
        </div>

        <section class="create-project-card">
          <h3>新建项目</h3>

          <div class="create-project-form">
            <input
              v-model="draftProjectCode"
              placeholder="项目编号，例如 P002"
            />

            <input
              v-model="draftProjectName"
              placeholder="项目名称"
            />

            <input
              v-model="draftProjectDescription"
              placeholder="项目描述，可选"
            />

            <button
              class="primary-button"
              :disabled="!selectedWorkspace || creatingProject"
              @click="handleCreateProject"
            >
              {{ creatingProject ? '创建中...' : '创建项目' }}
            </button>
          </div>
        </section>

        <div v-if="loadingProjects" class="empty-hint">
          正在加载项目列表...
        </div>

        <div v-else-if="projects.length === 0" class="empty-hint">
          当前工作空间还没有项目。可以在上方创建一个新项目。
        </div>

        <div v-else class="project-card-grid">
          <article
            v-for="project in projects"
            :key="project.projectId"
            class="project-card-item"
          >
            <div class="project-card-header">
              <div>
                <h3>{{ project.projectName }}</h3>
                <span>{{ project.projectCode }}</span>
              </div>

              <em>{{ project.status }}</em>
            </div>

            <p class="project-description">
              {{ project.description || '暂无项目描述' }}
            </p>

            <div class="project-stat-row">
              <div>
                <span>模块</span>
                <strong>{{ project.moduleCount }}</strong>
              </div>

              <div>
                <span>工程资产</span>
                <strong>{{ project.artifactCount }}</strong>
              </div>

              <div>
                <span>可见性</span>
                <strong>{{ project.visibility }}</strong>
              </div>
            </div>

            <button
              class="open-project-button"
              @click="$emit('open-project', project)"
            >
              打开项目工作台
            </button>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  CreateProjectRequest,
  CurrentUser,
  ProjectSummary,
  WorkspaceSummary,
} from '../types/codemind'

const props = defineProps<{
  currentUser: CurrentUser | null
  workspaces: WorkspaceSummary[]
  selectedWorkspaceId: string | null
  projects: ProjectSummary[]
  loadingProjects: boolean
  creatingProject: boolean
}>()

const emit = defineEmits<{
  'select-workspace': [workspace: WorkspaceSummary]
  'open-project': [project: ProjectSummary]
  'create-project': [payload: CreateProjectRequest]
  logout: []
}>()

const draftProjectCode = ref('')
const draftProjectName = ref('')
const draftProjectDescription = ref('')

const selectedWorkspace = computed(() => {
  return props.workspaces.find(
    (workspace) => workspace.workspaceId === props.selectedWorkspaceId,
  )
})

function handleCreateProject() {
  const projectCode = draftProjectCode.value.trim()
  const projectName = draftProjectName.value.trim()

  if (!projectCode || !projectName) {
    return
  }

  emit('create-project', {
    projectCode,
    projectName,
    description: draftProjectDescription.value.trim(),
    techStack: {
      frontend: 'Vue3 + TypeScript',
      backend: 'Spring Boot 3',
      agent: 'FastAPI + LangGraph',
      database: 'PostgreSQL',
    },
  })

  draftProjectCode.value = ''
  draftProjectName.value = ''
  draftProjectDescription.value = ''
}
</script>