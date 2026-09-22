import type { PageResult, Permission, Role } from '@/types'
import { del, get, getPage, post } from '@/api/http'

export function pageRoles(current = 1, size = 10): Promise<PageResult<Role>> {
  return getPage<Role>('/api/v1/rbac/roles', { current, size })
}

export function listRoles(): Promise<Role[]> {
  return get<Role[]>('/api/v1/rbac/roles/all')
}

export function createRole(payload: { roleCode: string; roleName?: string; remark?: string }): Promise<Role> {
  return post<Role>('/api/v1/rbac/roles', payload)
}

export function pagePermissions(current = 1, size = 20): Promise<PageResult<Permission>> {
  return getPage<Permission>('/api/v1/rbac/permissions', { current, size })
}

export function grantRole(userId: string, roleId: string): Promise<void> {
  return post('/api/v1/rbac/user-roles', { userId, roleId })
}

export function revokeRole(userId: string, roleId: string): Promise<void> {
  return del('/api/v1/rbac/user-roles', { userId, roleId })
}

export function bindPermission(roleId: string, permissionId: string): Promise<void> {
  return post('/api/v1/rbac/role-permissions', { roleId, permissionId })
}

export function syncUserRef(username: string): Promise<void> {
  return post(`/api/v1/user-refs/sync?username=${encodeURIComponent(username)}`)
}
