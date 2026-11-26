const JSON_HEADERS = { 'Content-Type': 'application/json' }

export async function login(payload) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
}

export async function register(payload) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
}

export async function logout() {
  return request('/api/auth/logout', { method: 'POST' })
}

export async function getCurrentUser() {
  return request('/api/auth/me')
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: JSON_HEADERS,
    ...options,
  })

  const body = await response.json().catch(() => ({}))

  if (!response.ok || body.success === false) {
    throw new Error(body?.error?.message || '请求失败')
  }

  return body.data
}
