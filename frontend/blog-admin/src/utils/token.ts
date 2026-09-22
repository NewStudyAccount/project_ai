/**
 * 令牌策略对齐 auth-portal：password 不落存储；access 内存+session；refresh session。
 */
const ACCESS_KEY = 'blog_admin_access'
const REFRESH_KEY = 'blog_admin_refresh'

let memoryAccess = ''

export function setAccessToken(token: string) {
  memoryAccess = token
  try {
    sessionStorage.setItem(ACCESS_KEY, token)
  } catch {
    // ignore
  }
}

export function getAccessToken(): string {
  return memoryAccess || sessionStorage.getItem(ACCESS_KEY) || ''
}

export function setRefreshToken(token: string) {
  sessionStorage.setItem(REFRESH_KEY, token)
}

export function getRefreshToken(): string {
  return sessionStorage.getItem(REFRESH_KEY) || ''
}

export function clearTokens() {
  memoryAccess = ''
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}
