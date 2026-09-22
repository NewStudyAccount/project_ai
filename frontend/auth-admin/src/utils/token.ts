const ACCESS_KEY = 'auth_admin_at'
const REFRESH_KEY = 'auth_admin_rt'
const ID_KEY = 'auth_admin_id_token'

export function saveTokens(at: string, rt: string, idToken?: string) {
  sessionStorage.setItem(ACCESS_KEY, at)
  sessionStorage.setItem(REFRESH_KEY, rt)
  if (idToken) sessionStorage.setItem(ID_KEY, idToken)
}

export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_KEY)
}

export function clearTokens() {
  sessionStorage.removeItem(ACCESS_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
  sessionStorage.removeItem(ID_KEY)
}
