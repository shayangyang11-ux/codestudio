import cors from 'cors'
import express from 'express'
import { registerGeneratedRoutes } from './routes/index.js'
import { sendError } from './shared/response.js'

const app = express()

app.use(cors())
app.use(express.json({ limit: '2mb' }))

registerGeneratedRoutes(app)

app.use((error, request, response, next) => {
  console.error(error)
  return sendError(response, error, error.status || 500)
})

export default app
