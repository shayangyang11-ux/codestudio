import express from 'express'
import cors from 'cors'
import { registerGeneratedRoutes } from './routes/index.js'
import { sendError } from './shared/response.js'

export function createApp() {
  const app = express()
  app.use(cors())
  app.use(express.json())

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true, service: 'codemind-generated-backend' })
  })

  registerGeneratedRoutes(app)

  app.use((error, _req, res, _next) => {
    sendError(res, error)
  })

  return app
}
