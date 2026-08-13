
from __future__ import annotations
from typing import Any


def _text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def has_inbound_order_resource(resources: list[dict[str, Any]] | None) -> bool:
    for item in resources or []:
        label = " ".join([
            _text(item.get("title")),
            _text(item.get("pageKey")),
            _text(item.get("resourceKey")),
            _text(item.get("routePath")),
            _text(item.get("apiSegment")),
        ]).lower()
        if "入库" in label or "inbound" in label or "order" in label:
            return True
    return False


def render_inbound_order_page(component_name: str, title: str = "", description: str = "", api_segment: str = "inbound/orders", route_path: str = "") -> str:
    label = " ".join([component_name or "", title or "", route_path or ""]).lower()
    zh = " ".join([component_name or "", title or "", route_path or ""])
    if "create" in label or "创建" in zh or "新建" in zh or "新增" in zh or "录入" in zh:
        return _create_page()
    if "approval" in label or "approve" in label or "审核" in zh or "审批" in zh:
        return _approval_page()
    if "quantity" in label or "edit" in label or "数量" in zh or "修改" in zh or "编辑" in zh:
        return _quantity_edit_page()
    if "detail" in label or "详情" in zh or "明细" in zh or "查看" in zh:
        return _detail_page()
    return _list_page()


def render_inbound_backend_routes_index() -> str:
    return '''import express from 'express'

const router = express.Router()

const orders = [
  {
    id: 'IN-20260806-001',
    code: 'IN-20260806-001',
    supplier: '华东供应链有限公司',
    warehouse: '上海主仓',
    status: '待审核',
    quantity: 120,
    expectedArrival: '2026-08-06 16:00',
    contact: '张经理',
    phone: '13800010001',
    remark: '高优先级入库，需到货后立即安排质检。',
    updatedAt: '2026-08-06 10:20:00',
    items: [
      { sku: 'SKU-AX-1001', name: '智能传感器组件', plannedQty: 60, actualQty: 60, unit: '件', location: 'A-01-03' },
      { sku: 'SKU-AX-1002', name: '车载通信模块', plannedQty: 60, actualQty: 60, unit: '件', location: 'A-01-04' },
    ],
    auditHistory: [
      { time: '2026-08-06 09:10', operator: '系统', action: '创建单据', comment: '从采购到货计划生成入库单。' },
      { time: '2026-08-06 09:45', operator: '仓储专员', action: '提交审核', comment: '供应商和预计到货时间已确认。' },
    ],
  },
  {
    id: 'IN-20260806-002',
    code: 'IN-20260806-002',
    supplier: '智能制造配件厂',
    warehouse: '苏州备件仓',
    status: '已提交',
    quantity: 80,
    expectedArrival: '2026-08-07 09:30',
    contact: '李主管',
    phone: '13800010002',
    remark: '备件补货。',
    updatedAt: '2026-08-06 11:05:00',
    items: [
      { sku: 'SKU-BX-2001', name: '制动控制器', plannedQty: 40, actualQty: 40, unit: '件', location: 'B-02-01' },
      { sku: 'SKU-BX-2002', name: '线束套件', plannedQty: 40, actualQty: 40, unit: '箱', location: 'B-02-02' },
    ],
    auditHistory: [
      { time: '2026-08-06 10:40', operator: '仓储专员', action: '提交审核', comment: '等待主管复核。' },
    ],
  },
  {
    id: 'IN-20260806-003',
    code: 'IN-20260806-003',
    supplier: '北区运输服务部',
    warehouse: '北京中心仓',
    status: '异常待处理',
    quantity: 36,
    expectedArrival: '2026-08-07 14:20',
    contact: '王工',
    phone: '13800010003',
    remark: '运输途中数量差异，需确认。',
    updatedAt: '2026-08-06 13:18:00',
    items: [
      { sku: 'SKU-CX-3001', name: '毫米波雷达支架', plannedQty: 40, actualQty: 36, unit: '件', location: 'C-03-09' },
    ],
    auditHistory: [
      { time: '2026-08-06 12:10', operator: '系统', action: '标记异常', comment: '到货数量与计划存在差异。' },
    ],
  },
]

function ok(response, data, message = 'OK') {
  return response.json({ success: true, code: 0, message, data })
}

function fail(response, message = 'Request failed', status = 400, details = undefined) {
  return response.status(status).json({ success: false, code: status, message, details })
}

function nextCode() {
  return `IN-20260806-${String(orders.length + 1).padStart(3, '0')}`
}

function findOrder(id) {
  return orders.find((item) => item.id === id || item.code === id)
}

function listOrders(request, response) {
  const keyword = String(request.query.keyword || '').trim().toLowerCase()
  const filtered = keyword
    ? orders.filter((item) => [item.code, item.supplier, item.warehouse, item.status, item.contact].some((value) => String(value || '').toLowerCase().includes(keyword)))
    : orders
  return ok(response, { items: filtered, total: filtered.length, pageNo: 1, pageSize: 20 })
}

function createOrder(request, response) {
  const body = request.body || {}
  if (!body.supplier || !body.warehouse) return fail(response, '供应商和仓库不能为空。', 400)
  const quantity = Number(body.quantity || 0)
  if (!Number.isFinite(quantity) || quantity <= 0) return fail(response, '入库数量必须大于 0。', 400)
  const code = nextCode()
  const now = new Date().toISOString().slice(0, 16).replace('T', ' ')
  const order = {
    id: code,
    code,
    supplier: body.supplier,
    warehouse: body.warehouse,
    status: '待审核',
    quantity,
    expectedArrival: body.expectedArrival || '',
    contact: body.contact || '',
    phone: body.phone || '',
    remark: body.remark || '',
    updatedAt: now,
    items: Array.isArray(body.items) && body.items.length ? body.items : [
      { sku: body.sku || 'SKU-NEW-001', name: body.itemName || '新建入库物料', plannedQty: quantity, actualQty: quantity, unit: body.unit || '件', location: body.location || body.warehouse },
    ],
    auditHistory: [{ time: now, operator: '当前用户', action: '创建入库单', comment: body.remark || '手工录入入库单。' }],
  }
  orders.unshift(order)
  return ok(response, order, '入库单已创建')
}

function getOrder(request, response) {
  const order = findOrder(request.params.id)
  if (!order) return fail(response, '入库单不存在。', 404)
  return ok(response, order)
}

function approveOrder(request, response) {
  const order = findOrder(request.params.id)
  if (!order) return fail(response, '入库单不存在。', 404)
  const decision = request.body?.decision === 'REJECT' ? 'REJECT' : 'APPROVE'
  const now = new Date().toISOString().slice(0, 16).replace('T', ' ')
  order.status = decision === 'APPROVE' ? '已审核' : '已驳回'
  order.updatedAt = now
  order.auditHistory = [...(order.auditHistory || []), {
    time: now,
    operator: request.body?.operator || '审核员',
    action: decision === 'APPROVE' ? '审核通过' : '审核驳回',
    comment: request.body?.comment || (decision === 'APPROVE' ? '审核通过。' : '审核驳回。'),
  }]
  return ok(response, order, decision === 'APPROVE' ? '审核已通过' : '审核已驳回')
}

function updateQuantity(request, response) {
  const order = findOrder(request.params.id)
  if (!order) return fail(response, '入库单不存在。', 404)
  const quantity = Number(request.body?.quantity)
  if (!Number.isFinite(quantity) || quantity <= 0) return fail(response, '调整后的数量必须大于 0。', 400)
  const before = Number(order.quantity || 0)
  const now = new Date().toISOString().slice(0, 16).replace('T', ' ')
  order.quantity = quantity
  order.status = '数量已调整'
  order.updatedAt = now
  order.auditHistory = [...(order.auditHistory || []), {
    time: now,
    operator: request.body?.operator || '仓储专员',
    action: '调整入库数量',
    comment: `${request.body?.reason || '数量调整'}：${before} → ${quantity}`,
  }]
  if (Array.isArray(order.items) && order.items.length) order.items[0].actualQty = quantity
  return ok(response, { order, beforeQuantity: before, afterQuantity: quantity, inventorySyncStatus: '已同步库存台账' }, '入库数量已更新')
}

function mountCollection(base) {
  router.get(base, listOrders)
  router.post(base, createOrder)
  router.get(`${base}/:id`, getOrder)
  router.post(`${base}/:id/approval`, approveOrder)
  router.patch(`${base}/:id/quantity`, updateQuantity)
}

mountCollection('/inbound/orders')
mountCollection('/inbound-orders')
mountCollection('/inbound-order-list-page')

router.get('/health', (request, response) => {
  ok(response, { service: 'codemind-generated-backend', status: 'UP', generatedAt: new Date().toISOString() })
})

export function registerGeneratedRoutes(app) {
  app.get('/health', (request, response) => {
    ok(response, { service: 'codemind-generated-backend', status: 'UP', generatedAt: new Date().toISOString() })
  })
  app.get('/api/health', (request, response) => {
    ok(response, { service: 'codemind-generated-backend', status: 'UP', generatedAt: new Date().toISOString() })
  })
  app.use('/api', router)
  return app
}

export default router
'''


def render_inbound_seed_data_js() -> str:
    return '''export const seedData = {
  inbound_orders: [
    { id: 'IN-20260806-001', code: 'IN-20260806-001', supplier: '华东供应链有限公司', warehouse: '上海主仓', status: '待审核', quantity: 120, expectedArrival: '2026-08-06 16:00' },
    { id: 'IN-20260806-002', code: 'IN-20260806-002', supplier: '智能制造配件厂', warehouse: '苏州备件仓', status: '已提交', quantity: 80, expectedArrival: '2026-08-07 09:30' },
    { id: 'IN-20260806-003', code: 'IN-20260806-003', supplier: '北区运输服务部', warehouse: '北京中心仓', status: '异常待处理', quantity: 36, expectedArrival: '2026-08-07 14:20' }
  ]
}
'''


def render_inbound_smoke_spec() -> str:
    return '''import http from 'node:http'
import app from '../backend/src/app.js'

const server = app.listen(0)
const port = await new Promise((resolve) => {
  server.once('listening', () => resolve(server.address().port))
})

try {
  const health = await request('/api/health')
  assert(health.success, 'health endpoint must return success')

  const list = await request('/api/inbound/orders')
  assert(list.success, 'list endpoint must return success')
  assert(Array.isArray(list.data.items), 'list endpoint must return data.items array')

  const created = await request('/api/inbound/orders', {
    method: 'POST',
    body: JSON.stringify({ supplier: 'Smoke Test Supplier', warehouse: 'Smoke Test Warehouse', quantity: 12, expectedArrival: '2026-08-08 10:00' }),
  })
  assert(created.success, 'create endpoint must return success')
  assert(created.data.id, 'created record must have id')

  const detail = await request(`/api/inbound/orders/${created.data.id}`)
  assert(detail.success, 'detail endpoint must return success')

  const approval = await request(`/api/inbound/orders/${created.data.id}/approval`, {
    method: 'POST',
    body: JSON.stringify({ decision: 'APPROVE', comment: 'Smoke approval' }),
  })
  assert(approval.success, 'approval endpoint must return success')

  const quantity = await request(`/api/inbound/orders/${created.data.id}/quantity`, {
    method: 'PATCH',
    body: JSON.stringify({ quantity: 16, reason: 'Smoke quantity adjustment' }),
  })
  assert(quantity.success, 'quantity endpoint must return success')
  assert(quantity.data.afterQuantity === 16, 'quantity endpoint must update quantity')

  console.log('Smoke tests passed')
} finally {
  await new Promise((resolve) => server.close(resolve))
}

function request(path, options = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request({ hostname: '127.0.0.1', port, path, method: options.method || 'GET', headers: { 'Content-Type': 'application/json' } }, (res) => {
      let text = ''
      res.setEncoding('utf8')
      res.on('data', (chunk) => { text += chunk })
      res.on('end', () => {
        try {
          const body = text ? JSON.parse(text) : {}
          if (res.statusCode >= 400) reject(new Error(body?.message || body?.error?.message || text || `HTTP ${res.statusCode}`))
          else resolve(body)
        } catch (error) {
          reject(error)
        }
      })
    })
    req.on('error', reject)
    if (options.body) req.write(options.body)
    req.end()
  })
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}
'''


COMMON_STYLE = '''
<style scoped>
.page { display: flex; flex-direction: column; gap: 22px; }
.page-header, .card, .stats-grid article, .sub-card { background: #fff; border: 1px solid #dbe5f5; border-radius: 18px; box-shadow: 0 18px 40px rgba(15,23,42,.06); padding: 26px 28px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.eyebrow { color: #2563eb; font-weight: 700; letter-spacing: .08em; margin: 0 0 8px; }
h2 { margin: 0 0 10px; font-size: 28px; }
p { color: #475569; }
.secondary-link, .primary-link, a { color: #2563eb; text-decoration: none; font-weight: 700; }
.primary-link, button { border: 0; background: #2563eb; color: #fff; border-radius: 10px; padding: 10px 16px; cursor: pointer; }
.ghost { background: #e0e7ff; color: #1d4ed8; }
.danger { background: #dc2626; }
input, select, textarea { border: 1px solid #cbd5e1; border-radius: 10px; padding: 10px 12px; font: inherit; }
textarea { min-height: 92px; resize: vertical; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 20px; }
label { display: flex; flex-direction: column; gap: 8px; color: #334155; font-weight: 700; }
.wide { grid-column: 1 / -1; }
.actions, .toolbar { display: flex; gap: 12px; }
.message, .hint { color: #2563eb; font-weight: 700; }
.tag { background: #eff6ff; color: #1d4ed8; border-radius: 999px; padding: 4px 10px; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; padding: 12px 10px; border-bottom: 1px solid #e5e7eb; }
th { color: #475569; font-size: 13px; }
.stats-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.stats-grid span { display: block; color: #64748b; margin-bottom: 8px; }
.stats-grid strong { font-size: 28px; }
.info-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
dt { color: #64748b; }
dd { margin: 4px 0 0; font-weight: 700; }
.timeline { padding-left: 20px; }
.timeline span { display: block; color: #64748b; margin: 4px 0; }
.timeline p { margin-top: 0; }
.order-list { list-style: none; padding: 0; margin: 16px 0 0; display: flex; flex-direction: column; gap: 10px; }
.order-list li { border: 1px solid #dbe5f5; border-radius: 12px; padding: 12px; cursor: pointer; }
.order-list li.active { border-color: #2563eb; background: #eff6ff; }
.layout { display: grid; grid-template-columns: 360px 1fr; gap: 18px; }
.summary, .result { margin-top: 16px; background: #f8fafc; border: 1px solid #dbe5f5; border-radius: 12px; padding: 14px; display: flex; gap: 12px; align-items: center; }
</style>
'''


def _list_page() -> str:
    return '''<template>
  <section class="page">
    <header class="page-header">
      <div><p class="eyebrow">INBOUND ORDER MANAGEMENT</p><h2>入库单列表页</h2><p>用于查看、筛选和处理仓储入库单。</p></div>
      <RouterLink class="primary-link" to="/inbound/orders/create">新建入库单</RouterLink>
    </header>
    <div class="stats-grid">
      <article><span>待审核</span><strong>{{ pendingCount }}</strong></article>
      <article><span>今日入库</span><strong>{{ rows.length }}</strong></article>
      <article><span>异常单据</span><strong>{{ exceptionCount }}</strong></article>
    </div>
    <section class="card">
      <div class="toolbar"><input v-model="keyword" placeholder="搜索单号、供应商、仓库、状态" @keyup.enter="loadOrders" /><button @click="loadOrders">刷新数据</button></div>
      <p v-if="apiMessage" class="hint">{{ apiMessage }}</p>
      <table>
        <thead><tr><th>入库单号</th><th>供应商</th><th>仓库</th><th>状态</th><th>数量</th><th>预计到货</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in filteredRows" :key="item.id">
            <td>{{ item.code }}</td><td>{{ item.supplier }}</td><td>{{ item.warehouse }}</td><td><span class="tag">{{ item.status }}</span></td><td>{{ item.quantity }}</td><td>{{ item.expectedArrival }}</td>
            <td class="actions">
              <RouterLink :to="`/inbound/orders/detail?id=${encodeURIComponent(item.id)}`">详情</RouterLink>
              <RouterLink :to="`/inbound/orders/approval?id=${encodeURIComponent(item.id)}`">审核</RouterLink>
              <RouterLink :to="`/inbound/orders/quantity-edit?id=${encodeURIComponent(item.id)}`">改数量</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
const keyword = ref('')
const apiMessage = ref('')
const rows = ref([])
const filteredRows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter((item) => [item.code, item.supplier, item.warehouse, item.status].some((value) => String(value || '').toLowerCase().includes(q)))
})
const pendingCount = computed(() => rows.value.filter((item) => String(item.status).includes('待')).length)
const exceptionCount = computed(() => rows.value.filter((item) => String(item.status).includes('异常')).length)
async function loadOrders() {
  const params = keyword.value ? `?keyword=${encodeURIComponent(keyword.value)}` : ''
  const response = await fetch(`/api/inbound/orders${params}`)
  const payload = await response.json()
  rows.value = payload?.data?.items || []
  apiMessage.value = '已从后端接口加载入库单数据。'
}
onMounted(loadOrders)
</script>
''' + COMMON_STYLE


def _create_page() -> str:
    return '''<template>
  <section class="page">
    <header class="page-header"><div><p class="eyebrow">INBOUND ORDER CREATION</p><h2>入库单创建页</h2><p>录入供应商、仓库、预计到货时间、数量和明细，提交后写入 Node.js 后端。</p></div><RouterLink class="secondary-link" to="/inbound/orders">返回列表</RouterLink></header>
    <section class="card">
      <h3>基础信息</h3>
      <form class="form-grid" @submit.prevent="submitOrder">
        <label><span>供应商</span><input v-model.trim="draft.supplier" required placeholder="请输入供应商名称" /></label>
        <label><span>入库仓库</span><select v-model="draft.warehouse" required><option value="">请选择仓库</option><option>上海主仓</option><option>苏州备件仓</option><option>北京中心仓</option></select></label>
        <label><span>预计到货时间</span><input v-model="draft.expectedArrival" type="datetime-local" required /></label>
        <label><span>入库数量</span><input v-model.number="draft.quantity" type="number" min="1" required /></label>
        <label><span>联系人</span><input v-model.trim="draft.contact" placeholder="供应商联系人" /></label>
        <label><span>联系电话</span><input v-model.trim="draft.phone" placeholder="联系电话" /></label>
        <label class="wide"><span>备注</span><textarea v-model.trim="draft.remark" placeholder="填写到货注意事项、质检要求或异常说明" /></label>
        <div class="actions wide"><button type="submit" :disabled="submitting">{{ submitting ? '提交中...' : '提交入库单' }}</button><button type="button" class="ghost" @click="resetDraft">重置</button></div>
      </form>
      <p v-if="message" class="message">{{ message }}</p>
      <div v-if="createdOrder" class="result"><strong>已创建：{{ createdOrder.code }}</strong><span>{{ createdOrder.supplier }} / {{ createdOrder.warehouse }} / {{ createdOrder.quantity }} 件</span><RouterLink :to="`/inbound/orders/detail?id=${encodeURIComponent(createdOrder.id)}`">查看详情</RouterLink></div>
    </section>
  </section>
</template>
<script setup>
import { reactive, ref } from 'vue'
const submitting = ref(false)
const message = ref('')
const createdOrder = ref(null)
const draft = reactive({ supplier: '', warehouse: '', expectedArrival: '', quantity: 1, contact: '', phone: '', remark: '' })
function resetDraft() { Object.assign(draft, { supplier: '', warehouse: '', expectedArrival: '', quantity: 1, contact: '', phone: '', remark: '' }); createdOrder.value = null; message.value = '' }
async function submitOrder() {
  submitting.value = true; message.value = ''; createdOrder.value = null
  try {
    const response = await fetch('/api/inbound/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(draft) })
    const payload = await response.json()
    if (!response.ok || !payload.success) throw new Error(payload.message || `HTTP ${response.status}`)
    createdOrder.value = payload.data; message.value = payload.message || '入库单已创建。'
  } catch (error) { message.value = `提交失败：${error.message}` } finally { submitting.value = false }
}
</script>
''' + COMMON_STYLE


def _approval_page() -> str:
    return '''<template>
  <section class="page">
    <header class="page-header"><div><p class="eyebrow">INBOUND APPROVAL</p><h2>入库单审核页</h2><p>审批入库单、提交审核意见，并同步单据状态和审核记录。</p></div><RouterLink class="secondary-link" to="/inbound/orders">返回列表</RouterLink></header>
    <div class="layout">
      <section class="card"><h3>待处理单据</h3><button @click="loadOrders">刷新</button><ul class="order-list"><li v-for="item in rows" :key="item.id" :class="{ active: selected?.id === item.id }" @click="selectOrder(item)"><strong>{{ item.code }}</strong><span>{{ item.supplier }} / {{ item.status }}</span></li></ul></section>
      <section class="card">
        <template v-if="selected">
          <h3>{{ selected.code }}</h3>
          <dl class="info-grid"><div><dt>供应商</dt><dd>{{ selected.supplier }}</dd></div><div><dt>仓库</dt><dd>{{ selected.warehouse }}</dd></div><div><dt>数量</dt><dd>{{ selected.quantity }}</dd></div><div><dt>状态</dt><dd><span class="tag">{{ selected.status }}</span></dd></div></dl>
          <label><span>审核意见</span><textarea v-model.trim="comment" placeholder="请输入审核意见" /></label>
          <div class="actions"><button @click="submitApproval('APPROVE')">通过</button><button class="danger" @click="submitApproval('REJECT')">驳回</button></div>
          <p v-if="message" class="message">{{ message }}</p>
          <h4>审核记录</h4><ol class="timeline"><li v-for="history in selected.auditHistory || []" :key="`${history.time}-${history.action}`"><strong>{{ history.action }}</strong><span>{{ history.time }} · {{ history.operator }}</span><p>{{ history.comment }}</p></li></ol>
        </template>
        <p v-else>请选择待审核入库单。</p>
      </section>
    </div>
  </section>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const rows = ref([])
const selected = ref(null)
const comment = ref('资料齐全，同意入库。')
const message = ref('')
async function loadOrders() {
  const response = await fetch('/api/inbound/orders')
  const payload = await response.json()
  rows.value = payload?.data?.items || []
  const id = route.query.id
  selected.value = rows.value.find((item) => item.id === id || item.code === id) || rows.value.find((item) => String(item.status).includes('待')) || rows.value[0] || null
}
function selectOrder(item) { selected.value = item; message.value = '' }
async function submitApproval(decision) {
  if (!selected.value) return
  const response = await fetch(`/api/inbound/orders/${encodeURIComponent(selected.value.id)}/approval`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ decision, comment: comment.value }) })
  const payload = await response.json()
  if (!response.ok || !payload.success) { message.value = payload.message || `提交失败 HTTP ${response.status}`; return }
  selected.value = payload.data; rows.value = rows.value.map((item) => item.id === payload.data.id ? payload.data : item); message.value = payload.message
}
onMounted(loadOrders)
</script>
''' + COMMON_STYLE


def _quantity_edit_page() -> str:
    return '''<template>
  <section class="page">
    <header class="page-header"><div><p class="eyebrow">INBOUND QUANTITY CONTROL</p><h2>入库单数量编辑页</h2><p>调整入库数量、记录差异原因，并触发库存台账同步。</p></div><RouterLink class="secondary-link" to="/inbound/orders">返回列表</RouterLink></header>
    <section class="card">
      <div class="form-grid"><label><span>选择入库单</span><select v-model="selectedId" @change="syncSelected"><option v-for="item in rows" :key="item.id" :value="item.id">{{ item.code }} · {{ item.supplier }}</option></select></label><label><span>当前数量</span><input :value="selected?.quantity || 0" disabled /></label><label><span>调整后数量</span><input v-model.number="quantity" type="number" min="1" /></label><label><span>差异原因</span><input v-model.trim="reason" placeholder="例如：实收数量差异、破损剔除、复核调整" /></label></div>
      <div v-if="selected" class="summary"><strong>{{ selected.code }}</strong><span>{{ selected.supplier }} / {{ selected.warehouse }} / {{ selected.status }}</span></div>
      <div class="actions"><button @click="submitQuantity">保存数量调整</button><button class="ghost" @click="loadOrders">重新读取</button></div>
      <p v-if="message" class="message">{{ message }}</p>
    </section>
  </section>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const rows = ref([])
const selectedId = ref('')
const quantity = ref(1)
const reason = ref('实收到货数量复核调整')
const message = ref('')
const selected = computed(() => rows.value.find((item) => item.id === selectedId.value) || null)
async function loadOrders() { const response = await fetch('/api/inbound/orders'); const payload = await response.json(); rows.value = payload?.data?.items || []; selectedId.value = String(route.query.id || selectedId.value || rows.value[0]?.id || ''); syncSelected() }
function syncSelected() { if (selected.value) quantity.value = Number(selected.value.quantity || 1); message.value = '' }
async function submitQuantity() {
  if (!selected.value) return
  const response = await fetch(`/api/inbound/orders/${encodeURIComponent(selected.value.id)}/quantity`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ quantity: quantity.value, reason: reason.value }) })
  const payload = await response.json()
  if (!response.ok || !payload.success) { message.value = payload.message || `保存失败 HTTP ${response.status}`; return }
  rows.value = rows.value.map((item) => item.id === payload.data.order.id ? payload.data.order : item)
  message.value = `${payload.message}：${payload.data.beforeQuantity} → ${payload.data.afterQuantity}，${payload.data.inventorySyncStatus}`
}
onMounted(loadOrders)
</script>
''' + COMMON_STYLE


def _detail_page() -> str:
    return '''<template>
  <section class="page">
    <header class="page-header"><div><p class="eyebrow">INBOUND ORDER DETAIL</p><h2>入库单详情页</h2><p>查看入库单主信息、物料明细、状态流转和关联操作。</p></div><RouterLink class="secondary-link" to="/inbound/orders">返回列表</RouterLink></header>
    <section class="card">
      <div class="toolbar"><select v-model="selectedId" @change="loadDetail"><option v-for="item in rows" :key="item.id" :value="item.id">{{ item.code }} · {{ item.supplier }}</option></select><button @click="loadDetail">刷新详情</button></div>
      <template v-if="order">
        <dl class="info-grid"><div><dt>入库单号</dt><dd>{{ order.code }}</dd></div><div><dt>供应商</dt><dd>{{ order.supplier }}</dd></div><div><dt>仓库</dt><dd>{{ order.warehouse }}</dd></div><div><dt>状态</dt><dd><span class="tag">{{ order.status }}</span></dd></div><div><dt>数量</dt><dd>{{ order.quantity }}</dd></div><div><dt>预计到货</dt><dd>{{ order.expectedArrival }}</dd></div><div><dt>联系人</dt><dd>{{ order.contact || '-' }}</dd></div><div><dt>联系电话</dt><dd>{{ order.phone || '-' }}</dd></div></dl>
        <section class="sub-card"><h3>入库明细</h3><table><thead><tr><th>SKU</th><th>物料名称</th><th>计划数量</th><th>实收数量</th><th>单位</th><th>库位</th></tr></thead><tbody><tr v-for="item in order.items || []" :key="item.sku"><td>{{ item.sku }}</td><td>{{ item.name }}</td><td>{{ item.plannedQty }}</td><td>{{ item.actualQty }}</td><td>{{ item.unit }}</td><td>{{ item.location }}</td></tr></tbody></table></section>
        <section class="sub-card"><h3>状态流转</h3><ol class="timeline"><li v-for="history in order.auditHistory || []" :key="`${history.time}-${history.action}`"><strong>{{ history.action }}</strong><span>{{ history.time }} · {{ history.operator }}</span><p>{{ history.comment }}</p></li></ol></section>
      </template>
      <p v-if="message" class="message">{{ message }}</p>
    </section>
  </section>
</template>
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const rows = ref([])
const selectedId = ref('')
const order = ref(null)
const message = ref('')
async function loadOrders() { const response = await fetch('/api/inbound/orders'); const payload = await response.json(); rows.value = payload?.data?.items || []; selectedId.value = String(route.query.id || selectedId.value || rows.value[0]?.id || ''); await loadDetail() }
async function loadDetail() { if (!selectedId.value) return; message.value = ''; try { const response = await fetch(`/api/inbound/orders/${encodeURIComponent(selectedId.value)}`); const payload = await response.json(); if (!response.ok || !payload.success) throw new Error(payload.message || `HTTP ${response.status}`); order.value = payload.data } catch (error) { message.value = `详情加载失败：${error.message}` } }
onMounted(loadOrders)
</script>
''' + COMMON_STYLE
