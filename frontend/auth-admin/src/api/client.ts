import { http } from '@/api/request'
import type { ClientRequest, ClientSecretVO, ClientVO, PageQuery, PageResult } from '@/types'

export interface ClientPageQuery extends PageQuery {
  clientId?: string
  systemCode?: string
  enabled?: number
}

export const clientApi = {
  page: (params: ClientPageQuery) => http.get<PageResult<ClientVO>>('/clients', params),
  create: (data: ClientRequest) => http.post<ClientSecretVO>('/clients', data),
  update: (id: string, data: ClientRequest) => http.put<ClientVO>('/clients/' + id, data),
  updateStatus: (id: string, enabled: number) => http.post<ClientVO>('/clients/' + id + '/status', { enabled }),
  resetSecret: (id: string) => http.post<ClientSecretVO>('/clients/' + id + '/secret'),
  remove: (id: string) => http.delete<void>('/clients/' + id),
}
