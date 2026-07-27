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

      <div class="user-menu" @click.stop>
        <button
          class="user-menu-trigger"
          type="button"
          @click="toggleUserMenu"
        >
          <span class="user-menu-caption">当前用户</span>
          <strong>{{ currentUser?.displayName || currentUser?.username || '-' }}</strong>
          <span class="user-menu-caret">{{ userMenuOpen ? '▴' : '▾' }}</span>
        </button>

        <div
          v-if="userMenuOpen"
          class="user-menu-popover"
        >
          <div class="user-menu-summary">
            <span>当前用户</span>
            <strong>{{ currentUser?.displayName || currentUser?.username || '-' }}</strong>
          </div>

          <button
            class="user-menu-logout"
            type="button"
            @click="handleLogoutClick"
          >
            退出登录
          </button>
        </div>
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

          <div class="create-project-form auto-code-form">
            <div class="auto-code-hint">
              项目编号将由系统自动生成，例如 P002、P003。
            </div>

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
              :disabled="!selectedWorkspace || creatingProject || !draftProjectName.trim()"
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

              <div class="project-card-actions" @click.stop>
                <em>{{ project.status }}</em>

                <button
                  class="project-more-button"
                  type="button"
                  title="项目操作"
                  @click="toggleProjectActionMenu(project.projectCode)"
                >
                  ⋯
                </button>

                <div
                  v-if="openProjectActionCode === project.projectCode"
                  class="project-action-popover"
                >
                  <button
                    type="button"
                    @click="handleOpenProjectFromMenu(project)"
                  >
                    打开项目工作台
                  </button>

                  <button
                    class="danger"
                    type="button"
                    :disabled="deletingProjectCode === project.projectCode"
                    @click="handleDeleteProject(project)"
                  >
                    {{ deletingProjectCode === project.projectCode ? '删除中...' : '删除项目' }}
                  </button>
                </div>
              </div>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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
  deletingProjectCode?: string | null
}>()

const emit = defineEmits<{
  'select-workspace': [workspace: WorkspaceSummary]
  'open-project': [project: ProjectSummary]
  'create-project': [payload: CreateProjectRequest]
  'delete-project': [project: ProjectSummary]
  logout: []
}>()

const draftProjectName = ref('')
const draftProjectDescription = ref('')
const userMenuOpen = ref(false)
const openProjectActionCode = ref<string | null>(null)

const selectedWorkspace = computed(() => {
  return props.workspaces.find(
    (workspace) => workspace.workspaceId === props.selectedWorkspaceId,
  )
})

function handleCreateProject() {
  const projectName = draftProjectName.value.trim()

  if (!projectName) {
    return
  }

  emit('create-project', {
    projectName,
    description: draftProjectDescription.value.trim(),
    techStack: {
      frontend: 'Vue3 + TypeScript',
      backend: 'Spring Boot 3',
      agent: 'FastAPI + LangGraph',
      database: 'PostgreSQL',
    },
  })

  draftProjectName.value = ''
  draftProjectDescription.value = ''
}

function toggleUserMenu() {
  openProjectActionCode.value = null
  userMenuOpen.value = !userMenuOpen.value
}

function handleLogoutClick() {
  userMenuOpen.value = false
  emit('logout')
}

function toggleProjectActionMenu(projectCode: string) {
  userMenuOpen.value = false
  openProjectActionCode.value =
    openProjectActionCode.value === projectCode
      ? null
      : projectCode
}

function handleOpenProjectFromMenu(project: ProjectSummary) {
  openProjectActionCode.value = null
  emit('open-project', project)
}

function handleDeleteProject(project: ProjectSummary) {
  const confirmed = window.confirm(
    `确认删除项目「${project.projectName}」？\n\n删除后该项目将从项目列表移除，后续如需恢复需要管理员处理。`,
  )

  if (!confirmed) {
    return
  }

  openProjectActionCode.value = null
  emit('delete-project', project)
}

function closeFloatingMenus() {
  userMenuOpen.value = false
  openProjectActionCode.value = null
}

onMounted(() => {
  document.addEventListener('click', closeFloatingMenus)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeFloatingMenus)
})
</script>

<style scoped>
.project-home-header {
  position: relative;
  z-index: 20;
}

.user-menu {
  position: relative;
  display: flex;
  justify-content: flex-end;
}

.user-menu-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 6px 10px 6px 12px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.88);
  color: #ffffff;
  cursor: pointer;
}

.user-menu-trigger:hover {
  border-color: rgba(191, 219, 254, 0.7);
  background: rgba(30, 41, 59, 0.96);
}

.user-menu-caption {
  color: #94a3b8;
  font-size: 11px;
}

.user-menu-trigger strong {
  font-size: 14px;
  white-space: nowrap;
}

.user-menu-caret {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 10px;
  height: 10px;
  color: #bfdbfe;
  font-size: 10px;
  line-height: 1;
}

.user-menu-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 60;
  min-width: 180px;
  padding: 10px;
  border: 1px solid #1f2937;
  border-radius: 12px;
  background: #0f172a;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.3);
}

.user-menu-summary {
  display: grid;
  gap: 4px;
  padding: 4px 4px 10px;
}

.user-menu-summary span {
  color: #94a3b8;
  font-size: 12px;
}

.user-menu-summary strong {
  color: #ffffff;
  font-size: 14px;
}

.user-menu-logout {
  width: 100%;
  height: 34px;
  border: 0;
  border-radius: 9px;
  background: #ffffff;
  color: #0f172a;
  font-weight: 700;
  cursor: pointer;
}

.user-menu-logout:hover {
  background: #eff6ff;
  color: #1d4ed8;
}

.auto-code-form {
  grid-template-columns: minmax(180px, 0.9fr) minmax(180px, 1fr) minmax(260px, 2fr) auto;
}

.auto-code-hint {
  display: flex;
  align-items: center;
  min-height: 40px;
  padding: 0 12px;
  border: 1px dashed #bfdbfe;
  border-radius: 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  white-space: nowrap;
}

.project-card-header {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.project-card-actions {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.project-more-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #ffffff;
  color: #2563eb;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.project-more-button:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.project-action-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 40;
  min-width: 150px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.16);
}

.project-action-popover button {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.project-action-popover button:hover {
  background: #f8fafc;
}

.project-action-popover button.danger {
  color: #dc2626;
}

.project-action-popover button.danger:hover {
  background: #fef2f2;
}

.project-action-popover button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 1100px) {
  .auto-code-form {
    grid-template-columns: 1fr;
  }

  .auto-code-hint {
    white-space: normal;
  }
}
</style>
