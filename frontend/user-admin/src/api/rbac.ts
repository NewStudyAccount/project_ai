import { http } from '@/api/request'
import type { MenuVO, RoleVO } from '@/types'

export interface MenuInput {
  parentId: string
  type: number
  name: string
  permission?: string
  path?: string
  component?: string
  icon?: string
  hidden?: number
  requiresAuth?: number
  sort?: number
  status?: number
}

export interface RoleInput {
  roleCode: string
  roleName: string
  dataScope: number
  sort: number
  status: number
  remark?: string
}

export const rbacApi = {
  menus: () => http.get<MenuVO[]>('/menus'),
  createMenu: (data: MenuInput) => http.post<MenuVO>('/menus', data),
  updateMenu: (id: string, data: MenuInput) => http.put<MenuVO>('/menus/' + id, data),
  deleteMenu: (id: string) => http.delete<void>('/menus/' + id),
  roles: () => http.get<RoleVO[]>('/roles'),
  createRole: (data: RoleInput) => http.post<RoleVO>('/roles', data),
  updateRole: (id: string, data: RoleInput) => http.put<RoleVO>('/roles/' + id, data),
  deleteRole: (id: string) => http.delete<void>('/roles/' + id),
  assignRoleMenus: (id: string, menuIds: string[]) => http.put<void>('/roles/' + id + '/menus', { menuIds }),
  myMenus: () => http.get<MenuVO[]>('/me/menus'),
  myPermissions: () => http.get<string[]>('/me/permissions'),
}
