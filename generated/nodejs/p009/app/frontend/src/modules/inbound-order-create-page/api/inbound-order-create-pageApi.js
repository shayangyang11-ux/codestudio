export async function fetchInboundOrderCreatePageRecords() {
  return request('/api/inbound-order-create-page')
}

export async function createInboundOrderCreatePageRecord(payload) {
  return request('/api/inbound-order-create-page', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  const body = await response.json().catch(() => ({}))

  if (!response.ok || body.success === false) {
    throw new Error(body?.error?.message || 'Request failed')
  }

  return body.data
}
