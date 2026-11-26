const actionBindings = [
  {
    "id": "back_to_list",
    "label": "返回列表",
    "method": "POST",
    "path": ""
  },
  {
    "id": "save_draft",
    "label": "保存草稿",
    "method": "POST",
    "path": ""
  },
  {
    "id": "submit_order",
    "label": "提交入库单",
    "method": "POST",
    "path": ""
  },
  {
    "id": "cancel_create",
    "label": "取消",
    "method": "POST",
    "path": ""
  },
  {
    "id": "add_item",
    "label": "添加商品",
    "method": "POST",
    "path": ""
  },
  {
    "id": "delete_item",
    "label": "删除",
    "method": "POST",
    "path": ""
  },
  {
    "id": "show_save_success",
    "label": "保存成功",
    "method": "POST",
    "path": ""
  },
  {
    "id": "show_save_error",
    "label": "保存失败",
    "method": "POST",
    "path": ""
  },
  {
    "id": "show_submit_success",
    "label": "提交成功",
    "method": "POST",
    "path": ""
  },
  {
    "id": "show_submit_error",
    "label": "提交失败",
    "method": "POST",
    "path": ""
  },
  {
    "id": "basic_info_form_submit",
    "label": "提交",
    "method": "POST",
    "path": ""
  },
  {
    "id": "basic_info_form_reset",
    "label": "重置",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_page_save_draft",
    "label": "保存草稿",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_page_submit",
    "label": "提交",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_page_cancel",
    "label": "取消",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_page_add_item",
    "label": "添加商品",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_page_remove_item",
    "label": "删除",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_action",
    "label": "入库单创建",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_page_basic_form_submit",
    "label": "提交",
    "method": "POST",
    "path": ""
  },
  {
    "id": "inbound_order_create_page_basic_form_reset",
    "label": "重置",
    "method": "POST",
    "path": ""
  }
]
const basePath = '/api/inbound-orders'

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
