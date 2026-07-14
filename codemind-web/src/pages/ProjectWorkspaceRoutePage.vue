<template>
  <ProjectWorkspacePage
    v-if="workspace && project"
    :workspace="workspace"
    :project="project"
    @back-home="backToHome"
  />

  <div
    v-else
    class="page-loading"
  >
    正在加载项目工作区...
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProjectWorkspacePage from './ProjectWorkspacePage.vue'
import { useAppSession } from '../composables/useAppSession'

const route = useRoute()
const router = useRouter()

const {
  selectedWorkspace,
  currentProject,
  bootstrapAuth,
  ensureProjectSelected,
} = useAppSession()

const workspace = computed(() => selectedWorkspace.value)
const project = computed(() => currentProject.value)

onMounted(async () => {
  await loadFromRoute()
})

watch(
  () => [
    route.params.workspaceId,
    route.params.projectCode,
  ],
  async () => {
    await loadFromRoute()
  },
)

async function loadFromRoute() {
  await bootstrapAuth()

  const workspaceId = getRouteParam(route.params.workspaceId)
  const projectCode = getRouteParam(route.params.projectCode)

  if (!workspaceId || !projectCode) {
    await router.replace({
      name: 'workspaces',
    })
    return
  }

  const result = await ensureProjectSelected(
    workspaceId,
    projectCode,
  )

  if (!result) {
    await router.replace({
      name: 'workspaces',
    })
  }
}

async function backToHome() {
  await router.push({
    name: 'workspaces',
  })
}

function getRouteParam(value: string | string[] | undefined): string {
  if (Array.isArray(value)) {
    return value[0] || ''
  }

  return value || ''
}
</script>