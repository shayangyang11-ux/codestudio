import { createRouter, createWebHistory } from 'vue-router'

export const generatedRoutes = [
  {
    path: '/',
    redirect: "/inbound/orders/create",
  },
  {
    path: "/inbound/orders/create",
    name: "InboundOrderCreatePage",
    title: "入库单创建页",
    meta: {
  "title": "入库单创建页",
  "pageKey": "inbound_order_create_page",
  "surfaceType": "SECONDARY_ROUTE",
  "navigationVisible": false,
  "entryMode": "ACTION_ROUTE",
  "apiSegment": "inbound-orders"
},
    component: () => import("../modules/generated/pages/InboundOrderCreatePage.vue"),
  },
  {
    path: "/inbound/orders",
    name: "InboundOrderListPage",
    title: "入库单列表页",
    meta: {
  "title": "入库单列表页",
  "pageKey": "inbound_order_list_page",
  "surfaceType": "MAIN_NAV_PAGE",
  "navigationVisible": true,
  "entryMode": "NAVIGATION",
  "apiSegment": "inbound-orders-2"
},
    component: () => import("../modules/generated/pages/InboundOrderListPage.vue"),
  },
  {
    path: "/inbound/orders/:id",
    name: "InboundOrderDetailPage",
    title: "入库单详情页",
    meta: {
  "title": "入库单详情页",
  "pageKey": "inbound_order_detail_page",
  "surfaceType": "SECONDARY_ROUTE",
  "navigationVisible": false,
  "entryMode": "ACTION_ROUTE",
  "apiSegment": "inbound-orders-3"
},
    component: () => import("../modules/generated/pages/InboundOrderDetailPage.vue"),
  },
  {
    path: "/inbound/orders/:id/edit-quantity",
    name: "InboundOrderQuantityEditPage",
    title: "入库单数量编辑页",
    meta: {
  "title": "入库单数量编辑页",
  "pageKey": "inbound_order_quantity_edit_page",
  "surfaceType": "SECONDARY_ROUTE",
  "navigationVisible": false,
  "entryMode": "ACTION_ROUTE",
  "apiSegment": "inbound-orders-4"
},
    component: () => import("../modules/generated/pages/InboundOrderQuantityEditPage.vue"),
  },
  {
    path: "/inbound/orders/approval",
    name: "InboundOrderApprovalPage",
    title: "入库单审核页",
    meta: {
  "title": "入库单审核页",
  "pageKey": "inbound_order_approval_page",
  "surfaceType": "SECONDARY_ROUTE",
  "navigationVisible": false,
  "entryMode": "ACTION_ROUTE",
  "apiSegment": "inbound-orders-5"
},
    component: () => import("../modules/generated/pages/InboundOrderApprovalPage.vue"),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: "/inbound/orders/create",
  }]

export const navigationRoutes = generatedRoutes.filter((route) => {
  return route.path && !route.redirect && route.meta?.navigationVisible !== false
})

export const router = createRouter({
  history: createWebHistory(),
  routes: generatedRoutes,
})

export default router
