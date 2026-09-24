import { http } from '@/api/request'
import type { AuditVO, PageResult } from '@/types'

export interface AuditQuery {
  current: number
  size: number
  action?: string
  actorUserId?: string
  targetUserId?: string
  beginTime?: string
  endTime?: string
}

export const auditApi = {
  page: (params: AuditQuery) => http.get<PageResult<AuditVO>>('/audit-logs', params),
}
