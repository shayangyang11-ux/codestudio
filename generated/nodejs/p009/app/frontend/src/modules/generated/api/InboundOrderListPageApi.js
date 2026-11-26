const actionBindings = [
  {
    "id": "inbound_order_list_page_query",
    "label": "查询",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_list_page_reset_filters",
    "label": "重置",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_list_page_create",
    "label": "新增",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_list_page_export",
    "label": "导出",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_list_page_delete_row",
    "label": "删除",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_list_page_view_detail",
    "label": "查看",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_query_action",
    "label": "入库单查询",
    "method": "POST",
    "path": ""
  }
]
const basePath = '/api/inbound-orders-2'

export async function fetchUiRows() {
  const data = await request(basePath)
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.items)) return data.items
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.rows)) return data.rows
  return data
}

export async function runUiAction(actionId, payload = {}) {
  const binding = actionBindings.find((item) => item.id === actionId) || {}
  const method = String(binding.method || 'POST').toUpperCase()
  const path = binding.path || `${basePath}/${encodeURIComponent(actionId || 'action')}`
  return request(path, {
    method,
    body: method === 'GET' ? undefined : JSON.stringify(payload),
  })
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.success === false) {
    throw new Error(body?.error?.message || body?.message || `HTTP ${response.status}`)
  }
  return body.data ?? body
}
