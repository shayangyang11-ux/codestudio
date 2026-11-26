import * as generatedResourceRoutes from '../modules/generated/generated-resource.routes.js'
import * as inboundOrderManagementRoutes from '../modules/inbound-order-management/inbound-order-management.routes.js'
import { ok } from '../shared/response.js'

function resolveRouter(moduleExports) {
  return (
    moduleExports.default ||
    moduleExports.router ||
    moduleExports.routes ||
    moduleExports.generatedResourceRouter ||
    moduleExports.inboundOrderManagementRouter ||
    null
  )
}

function mountIfRouter(app, path, moduleExports) {
  const router = resolveRouter(moduleExports)
  if (router && typeof router === 'function') {
    app.use(path, router)
    return true
  }
  return false
}

export function registerGeneratedRoutes(app) {
  app.get('/health', (req, res) => {
    ok(res, {
      status: 'UP',
      service: 'codemind-generated-backend',
      timestamp: new Date().toISOString(),
    })
  })

  app.get('/api/health', (req, res) => {
    ok(res, {
      status: 'UP',
      service: 'codemind-generated-backend',
      timestamp: new Date().toISOString(),
    })
  })

  mountIfRouter(app, '/api/generated', generatedResourceRoutes)
  mountIfRouter(app, '/api/inbound-order-management', inboundOrderManagementRoutes)
  mountIfRouter(app, '/api/inbound/orders', inboundOrderManagementRoutes)

  return app
}

export default registerGeneratedRoutes
