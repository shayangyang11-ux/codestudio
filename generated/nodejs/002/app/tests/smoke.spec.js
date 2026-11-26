const baseUrl = 'http://127.0.0.1:3101'

async function main() {
  const suffix = Date.now()
  const account = `demo_${suffix}`

  await assertOk(fetch(`${baseUrl}/api/health`), 'health should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, nickname: '自动验收用户', password: 'demo123', confirmPassword: 'demo123' }),
  }), 'register should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password: 'demo123' }),
  }), 'login should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/me`), 'me should be ok')
  await assertOk(fetch(`${baseUrl}/api/auth/logout`, { method: 'POST' }), 'logout should be ok')

  console.log('Auth smoke test passed.')
}

async function assertOk(promise, message) {
  const response = await promise
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`${message}: ${response.status} ${body}`)
  }
  return response
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
