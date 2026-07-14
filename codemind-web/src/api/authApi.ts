import type {
  CurrentUser,
  LoginRequest,
  LoginResponse,
} from '../types/codemind'
import {
  authFetch,
  publicFetch,
  setAuthStorage,
} from './httpClient'

export async function login(
  payload: LoginRequest,
): Promise<LoginResponse> {
  const loginResponse = await publicFetch<LoginResponse>(
    '/api/auth/login',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )

  setAuthStorage(
    loginResponse.accessToken,
    loginResponse.user,
  )

  return loginResponse
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  return authFetch<CurrentUser>('/api/auth/me')
}