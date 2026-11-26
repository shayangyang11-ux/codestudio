<template>
  <main :class="['app-shell', shellClass]">
    <aside v-if="isSidebar" class="app-sidebar">
      <strong>{{ appShell.title }}</strong>
      <span>{{ shellSubtitle }}</span>
      <nav v-if="navigationRoutes.length">
        <button
          v-for="route in navigationRoutes"
          :key="route.path"
          type="button"
          :class="{ active: currentPath === route.path }"
          @click="go(route.path)"
        >
          {{ route.meta?.title || route.title || route.name }}
        </button>
      </nav>
      <p v-else class="shell-hint">该模块没有固定导航，请通过页面动作流转。</p>
    </aside>

    <section class="app-main">
      <header v-if="isTopNav" class="app-topbar">
        <div>
          <strong>{{ appShell.title }}</strong>
          <span>{{ shellSubtitle }}</span>
        </div>
        <nav v-if="navigationRoutes.length">
          <button
            v-for="route in navigationRoutes"
            :key="route.path"
            type="button"
            :class="{ active: currentPath === route.path }"
            @click="go(route.path)"
          >
            {{ route.meta?.title || route.title || route.name }}
          </button>
        </nav>
      </header>

      <header v-if="isActionHub" class="app-action-hub">
        <div>
          <small>MODULE ACTION FLOW</small>
          <h1>{{ appShell.title }}</h1>
          <p>当前模块使用按钮和业务动作串联页面，没有强制固定导航栏。</p>
        </div>
      </header>

      <section v-if="currentActions.length" class="module-action-bar">
        <div>
          <small>页面动作</small>
          <strong>{{ currentRouteTitle }}</strong>
        </div>
        <div class="module-action-buttons">
          <button
            v-for="edge in currentActions"
            :key="edge.actionId + ':' + edge.targetRoute"
            type="button"
            @click="go(edge.targetRoute)"
          >
            {{ edge.actionLabel || edge.label || edge.targetPageName || '打开页面' }}
          </button>
        </div>
      </section>

      <section class="app-content">
        <RouterView />
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { navigationRoutes } from './router/routes.js'

const appShell = {
  "title": "入库单管理",
  "layout": "SIDEBAR",
  "navigation": [
    {
      "label": "入库单列表页",
      "order": 1,
      "route": "/inbound/orders",
      "pageKey": "inbound_order_list_page",
      "visible": true
    }
  ],
  "navigationPlacement": "left",
  "shellKind": "ADMIN_SIDEBAR",
  "defaultPageKey": "inbound_order_list_page",
  "defaultRoute": "/inbound/orders"
}
const navigationEdges = [
  {
    "label": "返回列表",
    "actionId": "back_to_list",
    "targetRoute": "/inbound/orders",
    "sourcePageKey": "inbound_order_create_page",
    "targetPageKey": "inbound_order_list_page",
    "navigationType": "IN_MODULE",
    "sourcePageName": "入库单创建页",
    "targetPageName": "入库单列表页",
    "navigationStatus": "RESOLVED_PAGE_ONLY",
    "sourceRoute": "/inbound/orders/create",
    "actionLabel": "返回列表",
    "interactionType": "ROUTE_NAVIGATION",
    "renderAs": "ACTION_BUTTON"
  },
  {
    "label": "新建",
    "actionId": "create_record",
    "targetRoute": "/inbound/orders/create",
    "sourcePageKey": "inbound_order_list_page",
    "targetPageKey": "inbound_order_create_page",
    "navigationType": "IN_MODULE",
    "sourcePageName": "入库单列表页",
    "targetPageName": "入库单创建页",
    "navigationStatus": "RESOLVED_PAGE_ONLY",
    "sourceRoute": "/inbound/orders",
    "actionLabel": "新建",
    "interactionType": "ROUTE_NAVIGATION",
    "renderAs": "ACTION_BUTTON"
  },
  {
    "label": "查看详情",
    "actionId": "view_detail",
    "targetRoute": "/inbound/orders/:id",
    "sourcePageKey": "inbound_order_list_page",
    "targetPageKey": "inbound_order_detail_page",
    "navigationType": "IN_MODULE",
    "sourcePageName": "入库单列表页",
    "targetPageName": "入库单详情页",
    "navigationStatus": "RESOLVED_PAGE_ONLY",
    "sourceRoute": "/inbound/orders",
    "actionLabel": "查看详情",
    "interactionType": "ROUTE_NAVIGATION",
    "renderAs": "ACTION_BUTTON"
  },
  {
    "label": "编辑",
    "actionId": "edit_record",
    "targetRoute": "/inbound/orders/:id/edit-quantity",
    "sourcePageKey": "inbound_order_detail_page",
    "targetPageKey": "inbound_order_quantity_edit_page",
    "navigationType": "IN_MODULE",
    "sourcePageName": "入库单详情页",
    "targetPageName": "入库单数量编辑页",
    "navigationStatus": "RESOLVED_PAGE_ONLY",
    "sourceRoute": "/inbound/orders/:id",
    "actionLabel": "编辑",
    "interactionType": "ROUTE_NAVIGATION",
    "renderAs": "ACTION_BUTTON"
  },
  {
    "label": "提交审核",
    "actionId": "submit_approval",
    "targetRoute": "/inbound/orders/approval",
    "sourcePageKey": "inbound_order_detail_page",
    "targetPageKey": "inbound_order_approval_page",
    "navigationType": "IN_MODULE",
    "sourcePageName": "入库单详情页",
    "targetPageName": "入库单审核页",
    "navigationStatus": "RESOLVED_PAGE_ONLY",
    "sourceRoute": "/inbound/orders/:id",
    "actionLabel": "提交审核",
    "interactionType": "ROUTE_NAVIGATION",
    "renderAs": "ACTION_BUTTON"
  },
  {
    "label": "返回列表",
    "actionId": "back_to_list",
    "targetRoute": "/inbound/orders",
    "sourcePageKey": "inbound_order_quantity_edit_page",
    "targetPageKey": "inbound_order_list_page",
    "navigationType": "IN_MODULE",
    "sourcePageName": "入库单数量编辑页",
    "targetPageName": "入库单列表页",
    "navigationStatus": "RESOLVED_PAGE_ONLY",
    "sourceRoute": "/inbound/orders/:id/edit-quantity",
    "actionLabel": "返回列表",
    "interactionType": "ROUTE_NAVIGATION",
    "renderAs": "ACTION_BUTTON"
  },
  {
    "label": "返回列表",
    "actionId": "back_to_list",
    "targetRoute": "/inbound/orders",
    "sourcePageKey": "inbound_order_approval_page",
    "targetPageKey": "inbound_order_list_page",
    "navigationType": "IN_MODULE",
    "sourcePageName": "入库单审核页",
    "targetPageName": "入库单列表页",
    "navigationStatus": "RESOLVED_PAGE_ONLY",
    "sourceRoute": "/inbound/orders/approval",
    "actionLabel": "返回列表",
    "interactionType": "ROUTE_NAVIGATION",
    "renderAs": "ACTION_BUTTON"
  }
]

const route = useRoute()
const router = useRouter()

const currentPath = computed(() => route.path)
const currentPageKey = computed(() => String(route.meta?.pageKey || ''))
const currentRouteTitle = computed(() => String(route.meta?.title || route.title || route.name || '当前页面'))

const shellLayout = computed(() => String(appShell.layout || 'SIDEBAR').toUpperCase())
const isSidebar = computed(() => shellLayout.value === 'SIDEBAR')
const isTopNav = computed(() => shellLayout.value === 'TOP_NAV')
const isActionHub = computed(() => shellLayout.value === 'ACTION_HUB' || shellLayout.value === 'NO_NAV')
const shellClass = computed(() => `layout-${shellLayout.value.toLowerCase().replace(/_/g, '-')}`)
const shellSubtitle = computed(() => {
  if (isTopNav.value) return 'Top Navigation'
  if (isActionHub.value) return 'Action Flow'
  return 'Node.js Fullstack'
})

const currentActions = computed(() => {
  return navigationEdges.filter((edge) => {
    const sourcePageKey = String(edge.sourcePageKey || '')
    const sourceRoute = normalizeRoute(edge.sourceRoute || '')
    const targetRoute = normalizeRoute(edge.targetRoute || '')
    if (!targetRoute) return false
    return sourcePageKey === currentPageKey.value || sourceRoute === normalizeRoute(route.path)
  })
})

function normalizeRoute(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  return '/' + text.replace(/^\/+|\/+$/g, '')
}

function go(path) {
  const target = normalizeRoute(path)
  if (target) router.push(target)
}
</script>
