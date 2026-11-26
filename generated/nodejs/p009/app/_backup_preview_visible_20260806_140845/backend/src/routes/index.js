import express from 'express'
import { generatedResources } from '../modules/generated/generatedResources.js'
import { createGeneratedResourceRouter } from '../modules/generated/generated-resource.routes.js'
import { ok, sendOk } from '../shared/response.js'

const router = express.Router()

const normalizeSegment = (value) => String(value || '').replace(/^\/+|\/+$/g, '')
const resourceByKey = Object.fromEntries(generatedResources.map((item) => [item.resourceKey, item]))
const resourceBySegment = Object.fromEntries(
  generatedResources.map((item) => [
    normalizeSegment(item.apiSegment || item.resourceKey),
    item,
  ]),
)

router.get('/health', (request, response) => {
  ok(response, {
    service: 'codemind-generated-nodejs',
    status: 'UP',
    resourceCount: generatedResources.length,
    generatedAt: new Date().toISOString(),
  })
})

for (const resource of generatedResources) {
  const segment = normalizeSegment(resource.apiSegment || resource.resourceKey)
  if (segment) {
    router.use('/' + segment, createGeneratedResourceRouter(resource))
  }
}

function mountAlias(alias, targetSegment) {
  const resource = resourceBySegment[normalizeSegment(targetSegment)]
  const path = '/' + normalizeSegment(alias)
  if (resource && path !== '/') {
    router.use(path, createGeneratedResourceRouter(resource))
  }
}

// 兼容浏览器和验收里常用的业务语义路径
mountAlias('/inbound/orders', 'inbound-order-list-page')
mountAlias('/inbound/orders/create', 'inbound-order-create-page')
mountAlias('/inbound/orders/approval', 'inbound-order-approval-page')
mountAlias('/inbound/orders/quantity-edit', 'inbound-order-quantity-edit-page')
mountAlias('/inbound/orders/detail', 'inbound-order-detail-page')

export function registerGeneratedRoutes(app) {
  app.get('/health', (request, response) => {
    sendOk(response, {
      service: 'codemind-generated-backend',
      status: 'UP',
      generatedAt: new Date().toISOString(),
    })
  })

  app.get('/api/health', (request, response) => {
    sendOk(response, {
      service: 'codemind-generated-backend',
      status: 'UP',
      generatedAt: new Date().toISOString(),
    })
  })

  app.use('/api', router)
  return app
}

export default router
