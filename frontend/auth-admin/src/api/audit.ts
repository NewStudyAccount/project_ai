import { http } from '@/api/request'
import type { AuditVO, PageQuery, PageResult } from '@/types'

export interface LoginAttemptQuery extends PageQuery {
  username?: string
  userId?: string
  clientId?: string
  ip?: string
  success?: number
  beginTime?: string
  endTime?: string
}

export interface AuditLogQuery extends PageQuery {
  action?: string
  actorUserId?: string
  targetId?: string
  beginTime?: string
  endTime?: string
}

export const auditApi = {
  loginAttempts: (params: LoginAttemptQuery) => http.get<PageResult<AuditVO>>('/login-attempts', params),
  auditLogs: (params: AuditLogQuery) => http.get<PageResult<AuditVO>>('/audit-logs', params),
}
