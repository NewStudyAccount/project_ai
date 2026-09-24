import { http } from '@/api/request'
import type { MenuRequest, MenuVO, RoleRequest, RoleVO } from '@/types'

export const rbacApi = {
  menus: () => http.get<MenuVO[]>('/menus'),
  createMenu: (data: MenuRequest) => http.post<MenuVO>('/menus', data),
  updateMenu: (id: string, data: MenuRequest) => http.put<MenuVO>('/menus/' + id, data),
  deleteMenu: (id: string) => http.delete<void>('/menus/' + id),
  roles: () => http.get<RoleVO[]>('/roles'),
  createRole: (data: RoleRequest) => http.post<RoleVO>('/roles', data),
  updateRole: (id: string, data: RoleRequest) => http.put<RoleVO>('/roles/' + id, data),
  deleteRole: (id: string) => http.delete<void>('/roles/' + id),
  assignRoleMenus: (id: string, ids: string[]) => http.put<void>('/roles/' + id + '/menus', { ids }),
  assignUserRoles: (userId: string, ids: string[]) => http.post<void>('/users/' + userId + '/roles', { ids }),
  myMenus: () => http.get<MenuVO[]>('/me/menus'),
  myPermissions: () => http.get<string[]>('/me/permissions'),
}
