import { post } from './http'
import type { LoginResponse } from '../types'

export function login(username: string, password: string, clientId?: string) {
  return post<LoginResponse>('/api/v1/auth/login', { username, password, clientId })
}

export function logout() {
  return post<void>('/api/v1/auth/logout')
}
