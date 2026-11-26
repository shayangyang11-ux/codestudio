import { router as authRouter } from '../modules/auth-module/auth-module.routes.js'

export function registerGeneratedRoutes(app) {
  app.use('/api/auth', authRouter)
}
