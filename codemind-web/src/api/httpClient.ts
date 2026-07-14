import type { CurrentUser } from '../types/codemind'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

const ACCESS_TOKEN_KEY = 'codemind:accessToken'
const CURRENT_USER_KEY = 'codemind:currentUser'

export function getStoredAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getStoredCurrentUser(): CurrentUser | null {
  const raw = localStorage.getItem(CURRENT_USER_KEY)

  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    localStorage.removeItem(CURRENT_USER_KEY)
    return null
  }
}

export function setAuthStorage(
  accessToken: string,
  currentUser: CurrentUser,
) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(currentUser))
}

export function clearAuthStorage() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(CURRENT_USER_KEY)
}

export async function publicFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  return baseFetch<T>(path, options, false)
}

export async function authFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  return baseFetch<T>(path, options, true)
}

export async function authBlobFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(options.headers || {})

  if (!headers.has('Accept')) {
    headers.set(
      'Accept',
      'application/octet-stream, application/json, text/plain, */*',
    )
  }

  const token = getStoredAccessToken()

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const url =
    path.startsWith('http://') || path.startsWith('https://')
      ? path
      : `${API_BASE_URL}${path}`

  const response = await fetch(url, {
    ...options,
    headers,
  })

  if (response.status === 401) {
    clearAuthStorage()
    throw new Error('UNAUTHORIZED')
  }

  if (!response.ok) {
    const errorText = await response.text()

    throw new Error(
      `HTTP ${response.status}: ${errorText || response.statusText}`,
    )
  }

  return response
}

async function baseFetch<T>(
  path: string,
  options: RequestInit = {},
  withAuth: boolean,
): Promise<T> {
  const headers = new Headers(options.headers || {})
  headers.set('Accept', 'application/json')

  const body = options.body

  const shouldAutoSetJsonContentType =
    Boolean(body) &&
    !headers.has('Content-Type') &&
    !(body instanceof FormData) &&
    !(body instanceof Blob) &&
    !(body instanceof URLSearchParams)

  if (shouldAutoSetJsonContentType) {
    headers.set('Content-Type', 'application/json; charset=utf-8')
  }

  if (withAuth) {
    const token = getStoredAccessToken()

    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  })

  if (response.status === 401) {
    clearAuthStorage()
    throw new Error('UNAUTHORIZED')
  }

  if (!response.ok) {
    const errorText = await response.text()

    throw new Error(
      `HTTP ${response.status}: ${errorText || response.statusText}`,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}