import { http } from '@/api/request'
import type { PageResult, UserVO } from '@/types'

export interface UserPageQuery {
  current: number
  size: number
  username?: string
  status?: number
}

export interface UserInput {
  username?: string
  realName?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  remark?: string
  gender?: number
  birthday?: string
  address?: string
  extraJson?: string
}

export const userApi = {
  page: (params: UserPageQuery) => http.get<PageResult<UserVO>>('/users', params),
  get: (id: string) => http.get<UserVO>('/users/' + id),
  create: (data: UserInput) => http.post<UserVO>('/users', data),
  update: (id: string, data: UserInput) => http.put<UserVO>('/users/' + id, data),
  updateStatus: (id: string, status: number) => http.post<UserVO>('/users/' + id + '/status', { status }),
  assignRoles: (id: string, roleIds: string[]) => http.post<void>('/users/' + id + '/roles', { roleIds }),
  removeRoles: (id: string, roleIds: string[]) => http.delete<void>('/users/' + id + '/roles', { roleIds }),
}
