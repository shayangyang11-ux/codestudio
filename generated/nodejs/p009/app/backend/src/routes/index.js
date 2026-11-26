import express from 'express'

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
