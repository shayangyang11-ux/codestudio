import { createRouter, createWebHistory } from 'vue-router'

export const generatedRoutes = [
  {
    path: '/',
    redirect: "/inbound-order-list-page",
  },
  {
    path: "/inbound-order-list-page",
    name: "InboundOrderListPage",
    title: "入库单列表页",
    component: () => import("../modules/generated/pages/InboundOrderListPage.vue"),
  },
  {
    path: "/inbound-order-create-page",
    name: "InboundOrderCreatePage",
    title: "入库单创建页",
    component: () => import("../modules/generated/pages/InboundOrderCreatePage.vue"),
  },
  {
    path: "/inbound-order-approval-page",
    name: "InboundOrderApprovalPage",
    title: "入库单审核页",
    component: () => import("../modules/generated/pages/InboundOrderApprovalPage.vue"),
  },
  {
    path: "/inbound-order-quantity-edit-page",
    name: "InboundOrderQuantityEditPage",
    title: "入库单数量编辑页",
    component: () => import("../modules/generated/pages/InboundOrderQuantityEditPage.vue"),
  },
  {
    path: "/inbound-order-detail-page",
    name: "InboundOrderDetailPage",
    title: "入库单详情页",
    component: () => import("../modules/generated/pages/InboundOrderDetailPage.vue"),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: "/inbound-order-list-page",
  }]

export const navigationRoutes = generatedRoutes.filter((route) => route.path && !route.redirect && !route.path.includes(':'))

export const router = createRouter({
  history: createWebHistory(),
  routes: generatedRoutes,
})

export default router
