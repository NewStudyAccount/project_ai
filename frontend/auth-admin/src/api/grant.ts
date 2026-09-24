import { http } from '@/api/request'
import type { GrantVO, PageQuery, PageResult } from '@/types'

export interface GrantPageQuery extends PageQuery {
  userId?: string
  clientId?: string
  status?: number
}

export const grantApi = {
  page: (params: GrantPageQuery) => http.get<PageResult<GrantVO>>('/grants', params),
  revoke: (id: string, reason: string) => http.post<void>('/grants/' + id + '/revoke', undefined, { reason }),
  revokeByUser: (userId: string, reason: string) =>
    http.post<void>('/grants/users/' + userId + '/revoke', undefined, { reason }),
}
