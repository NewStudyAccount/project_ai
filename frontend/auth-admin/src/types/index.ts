export interface Result<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface PageQuery {
  current: number
  size: number
}

export interface ClientVO {
  id: string
  clientId: string
  clientName: string
  clientType: string
  clientAuthMethod: string
  grantTypes: string
  redirectUris: string
  scopes: string
  requirePkce: number
  requireConsent: number
  reuseRefreshTokens: number
  accessTokenTtlSec: number
  refreshTokenTtlSec: number
  systemCode: string
  owner: string
  env: string
  enabled: number
  remark: string
}

export interface ClientSecretVO {
  id: string
  clientId: string
  clientSecret: string
}

export interface ClientRequest {
  clientId: string
  clientSecret?: string
  clientName: string
  clientType: string
  clientAuthMethod: string
  grantTypes: string
  redirectUris: string
  scopes: string
  requirePkce: number
  requireConsent: number
  reuseRefreshTokens: number
  accessTokenTtlSec: number
  refreshTokenTtlSec: number
  systemCode: string
  owner: string
  env: string
  enabled: number
  remark: string
}

export interface GrantVO {
  id: string
  userId: string
  clientId: string
  scopes: string
  status: number
  revokedAt?: string
  revokeReason: string
}

export interface AuditVO {
  id: string
  action: string
  actorUserId: string
  targetType: string
  targetId: string
  detail: string
  ip: string
  createTime: string
}

export interface MenuVO {
  id: string
  parentId: string
  type: number
  name: string
  permission: string
  path: string
  component: string
  icon: string
  hidden: number
  requiresAuth: number
  sort: number
  status: number
  children: MenuVO[]
}

export interface MenuRequest {
  parentId: string
  type: number
  name: string
  permission: string
  path: string
  component: string
  icon: string
  hidden: number
  requiresAuth: number
  sort: number
  status: number
}

export interface RoleVO {
  id: string
  roleCode: string
  roleName: string
  dataScope: number
  sort: number
  status: number
  remark: string
  menuIds: string[]
}

export interface RoleRequest {
  roleCode: string
  roleName: string
  dataScope: number
  sort: number
  status: number
  remark: string
}

export interface IdsRequest {
  ids: string[]
}

export interface LoginOutcome {
  ok: boolean
  message?: string
}
