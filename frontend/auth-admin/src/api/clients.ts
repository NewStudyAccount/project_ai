import { get, post, put } from './http'
import type { AuthGrant, AuthAuditLog, LoginAttempt, OauthClientVo, PageResult, SecretResetVo } from '../types'

export function pageClients(keyword?: string, enabled?: number, current = 1, size = 10) {
  return get<PageResult<OauthClientVo>>('/api/v1/admin/clients', { keyword, enabled, current, size })
}

export function getClient(clientId: string) {
  return get<OauthClientVo>(`/api/v1/admin/clients/${clientId}`)
}

export function createClient(payload: Record<string, unknown>) {
  return post<SecretResetVo>('/api/v1/admin/clients', payload)
}

export function updateClient(clientId: string, payload: Record<string, unknown>) {
  return put<void>(`/api/v1/admin/clients/${clientId}`, payload)
}

export function setClientEnabled(clientId: string, enabled: boolean) {
  return post<void>(`/api/v1/admin/clients/${clientId}/enabled`, undefined, { enabled })
}

export function resetSecret(clientId: string) {
  return post<SecretResetVo>(`/api/v1/admin/clients/${clientId}/secret`)
}

export function pageGrants(userId?: string, clientId?: string, current = 1, size = 10) {
  return get<PageResult<AuthGrant>>('/api/v1/admin/grants', { userId, clientId, current, size })
}

export function kickUser(userId: string) {
  return post<number>(`/api/v1/admin/grants/kick/user/${userId}`)
}

export function kickClient(clientId: string) {
  return post<number>(`/api/v1/admin/grants/kick/client/${clientId}`)
}

export function pageLoginAttempts(username?: string, ip?: string, success?: number, current = 1, size = 10) {
  return get<PageResult<LoginAttempt>>('/api/v1/admin/login-attempts', { username, ip, success, current, size })
}

export function pageAuditLogs(action?: string, actorUserId?: string, current = 1, size = 10) {
  return get<PageResult<AuthAuditLog>>('/api/v1/admin/audit-logs', { action, actorUserId, current, size })
}
