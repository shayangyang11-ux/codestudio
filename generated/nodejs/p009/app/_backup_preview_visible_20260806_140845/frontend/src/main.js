import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './shared/styles/app.css'

// 路由配置
const routes = [
  {
    path: '/',
    redirect: '/inbound/orders'
  },
  {
    path: '/inbound/orders',
    name: 'InboundOrderList',
    component: () => import('./pages/inbound/InboundOrderListPage.vue'),
    meta: { title: '入库单列表页' }
  },
  {
    path: '/inbound/orders/create',
    name: 'InboundOrderCreate',
    component: () => import('./pages/inbound/InboundOrderCreatePage.vue'),
    meta: { title: '入库单创建页' }
  },
  {
    path: '/inbound/orders/:id',
    name: 'InboundOrderDetail',
    component: () => import('./pages/inbound/InboundOrderDetailPage.vue'),
    meta: { title: '入库单详情页' }
  },
  {
    path: '/inbound/orders/:id/approval',
    name: 'InboundOrderApproval',
    component: () => import('./pages/inbound/InboundOrderApprovalPage.vue'),
    meta: { title: '入库单审核页' }
  },
  {
    path: '/inbound/orders/:id/quantity-edit',
    name: 'InboundOrderQuantityEdit',
    component: () => import('./pages/inbound/InboundOrderQuantityEditPage.vue'),
    meta: { title: '入库单数量编辑页' }
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫（可扩展权限控制）
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 智能仓储管理系统`
  }
  next()
})

// 创建 Vue 应用
const app = createApp(App)

// 注册路由
app.use(router)

// 挂载应用
app.mount('#app')
