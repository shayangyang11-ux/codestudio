import { seedData } from '../../db/seedData.js'

const users = [...(seedData.authUsers || [])]
let currentSession = users.length > 0 ? toSafeUser(users[0]) : null

export async function register(payload = {}) {
  const account = String(payload.account || '').trim()
  const nickname = String(payload.nickname || account || '').trim()
  const password = String(payload.password || '').trim()
  const confirmPassword = String(payload.confirmPassword || password).trim()

  if (!account) throw badRequest('账号不能为空。')
  if (!password) throw badRequest('密码不能为空。')
  if (password !== confirmPassword) throw badRequest('两次输入的密码不一致。')
  if (users.some((user) => user.account === account)) throw badRequest('账号已存在。')

  const nextUser = { id: `user-${Date.now()}`, account, nickname: nickname || account, password, status: 'ACTIVE', createdAt: new Date().toISOString() }
  users.push(nextUser)
  return { user: toSafeUser(nextUser), message: '注册成功。' }
}

export async function login(payload = {}) {
  const account = String(payload.account || 'demo').trim()
  const password = String(payload.password || 'demo123').trim()
  const matched = users.find((user) => user.account === account && user.password === password)

  if (!matched) {
    const error = new Error('账号或密码错误。')
    error.status = 401
    throw error
  }

  currentSession = toSafeUser(matched)
  return { user: currentSession, token: `demo-token-${Date.now()}`, message: '登录成功。' }
}

export async function logout() {
  currentSession = null
  return { message: '已退出登录。' }
}

export async function getCurrentUser() {
  return { authenticated: Boolean(currentSession), user: currentSession || toSafeUser(users[0]) }
}

function toSafeUser(user) {
  if (!user) return null
  return { id: user.id, account: user.account, nickname: user.nickname, status: user.status || 'ACTIVE' }
}

function badRequest(message) {
  const error = new Error(message)
  error.status = 400
  return error
}
