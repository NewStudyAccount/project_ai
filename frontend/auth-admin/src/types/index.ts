/** 统一返回体；id 一律 string */
export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: string | number
  size: string | number
  current: string | number
}

export interface OauthClientVo {
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
  accessTokenTtlSec: number
  refreshTokenTtlSec: number
  systemCode: string
  owner: string
  env: string
  enabled: number
  remark: string
  createTime: string
}

export interface SecretResetVo {
  client: OauthClientVo
  clientSecret: string | null
}

export interface AuthGrant {
  id: string
  userId: string
  clientId: string
  sessionId: string | null
  scopes: string
  status: number
  revokedAt: string | null
  revokeReason: string
  createTime: string
}

export interface LoginAttempt {
  id: string
  username: string
  userId: string | null
  success: number
  failReason: string
  ip: string
  userAgent: string
  clientId: string
  createTime: string
}

export interface AuthAuditLog {
  id: string
  action: string
  actorUserId: string | null
  targetType: string
  targetId: string
  detail: string
  ip: string
  createTime: string
}
