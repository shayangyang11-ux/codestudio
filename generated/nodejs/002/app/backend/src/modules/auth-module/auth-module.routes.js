import { Router } from 'express'
import { getCurrentUser, login, logout, register } from './auth-module.service.js'
import { sendOk } from '../../shared/response.js'

export const router = Router()

router.post('/login', async (req, res, next) => {
  try { sendOk(res, await login(req.body || {})) } catch (error) { next(error) }
})

router.post('/register', async (req, res, next) => {
  try { sendOk(res, await register(req.body || {})) } catch (error) { next(error) }
})

router.post('/logout', async (_req, res, next) => {
  try { sendOk(res, await logout()) } catch (error) { next(error) }
})

router.get('/me', async (_req, res, next) => {
  try { sendOk(res, await getCurrentUser()) } catch (error) { next(error) }
})
