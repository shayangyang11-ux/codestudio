import http from 'node:http'
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
