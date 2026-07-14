import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from 'vue-router'
import { getStoredAccessToken } from '../api'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: () => {
      return getStoredAccessToken() ? '/workspaces' : '/login'
    },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../pages/LoginRoutePage.vue'),
    meta: {
      guestOnly: true,
    },
  },
  {
    path: '/workspaces',
    name: 'workspaces',
    component: () => import('../pages/WorkspacesPage.vue'),
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/workspaces/:workspaceId/projects/:projectCode',
    name: 'project-workspace',
    component: () => import('../pages/ProjectWorkspaceRoutePage.vue'),
    meta: {
      requiresAuth: true,
    },
    props: true,
  },
  {
    path: '/workspaces/:workspaceId/projects/:projectCode/tasks',
    name: 'project-task-center',
    component: () => import('../pages/ProjectTaskCenterPage.vue'),
    meta: {
      requiresAuth: true,
    },
    props: true,
  },
  {
    path: '/admin/agent-ops',
    name: 'admin-agent-ops',
    component: () => import('../pages/AdminAgentOpsPage.vue'),
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: () => {
      return getStoredAccessToken() ? '/workspaces' : '/login'
    },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = getStoredAccessToken()

  if (to.meta.requiresAuth && !token) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (to.meta.guestOnly && token) {
    return {
      name: 'workspaces',
    }
  }

  return true
})