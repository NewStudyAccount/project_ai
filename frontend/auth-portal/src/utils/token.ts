/**
 * 令牌本地策略：
 * - password 永不落存储
 * - access 放 memory（刷新丢失则走 refresh）
 * - refresh 放 sessionStorage（可清；勿 console 打印）
 */
const ACCESS_KEY = 'auth_access'
const REFRESH_KEY = 'auth_refresh'

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
